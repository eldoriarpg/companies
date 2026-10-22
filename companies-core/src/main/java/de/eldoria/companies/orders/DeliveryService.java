/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.util.Inventories;
import de.eldoria.companies.util.Threading;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Delivers items of workers to the orders of their company.
 * <p>
 * A delivery takes the matching items on the main thread, records them as delivered stacks in one transaction that
 * caps every line at what it still misses, and hands the surplus of every part back with that part's own item: into
 * the inventory, dropped when it is full, or into the item returns when the worker went offline. When the order is
 * complete afterwards, {@link OrderCompletionService} completes it.
 */
public final class DeliveryService {
    private final Plugin plugin;
    private final AOrderData orderData;
    private final OrderCompletionService completion;
    private final Configuration configuration;
    private volatile CachedMatcher cachedMatcher;

    /**
     * @param plugin        the plugin
     * @param orderData     order repository
     * @param completion    completes orders once everything was delivered
     * @param configuration configuration of the plugin
     */
    public DeliveryService(
            Plugin plugin, AOrderData orderData, OrderCompletionService completion, Configuration configuration) {
        this.plugin = plugin;
        this.orderData = orderData;
        this.completion = completion;
        this.configuration = configuration;
    }

    /**
     * The matcher of the current matching settings. It is built again once the settings were reloaded.
     *
     * @return the matcher
     */
    public ItemMatcher matcher() {
        var settings = configuration.orderSetting().matching();
        var cached = cachedMatcher;
        if (cached == null || cached.settings() != settings) {
            cached = new CachedMatcher(settings, ItemMatcher.of(settings, plugin.getLogger()));
            cachedMatcher = cached;
        }
        return cached.matcher();
    }

    /**
     * Delivers items to one line of an order. Has to be called on the main thread.
     *
     * @param worker  delivering player
     * @param company company of the worker, which claimed the order
     * @param order   order as it was loaded
     * @param line    line of the order
     * @param amount  the most items to deliver; capped by what the line misses
     * @return what was delivered
     */
    public CompletableFuture<DeliveryResult> deliver(
            Player worker, CompanyProfile company, FullOrder order, int line, int amount) {
        var matcher = matcher();
        var slots = ItemSources.collect(worker);
        var parts = order.line(line)
                .map(content -> ItemSources.take(
                        slots, content.line(), content.stack(), matcher, Math.min(amount, content.missing())))
                .orElse(List.of());
        return submit(worker, company, order, parts);
    }

    /**
     * Delivers every matching item the worker carries to all lines of an order. Has to be called on the main thread.
     *
     * @param worker  delivering player
     * @param company company of the worker, which claimed the order
     * @param order   order as it was loaded
     * @return what was delivered
     */
    public CompletableFuture<DeliveryResult> deliverAll(Player worker, CompanyProfile company, FullOrder order) {
        var matcher = matcher();
        var slots = ItemSources.collect(worker);
        List<DeliveryPart> parts = new ArrayList<>();
        for (var content : order.contents()) {
            if (content.missing() <= 0) continue;
            parts.addAll(ItemSources.take(slots, content.line(), content.stack(), matcher, content.missing()));
        }
        return submit(worker, company, order, parts);
    }

    private CompletableFuture<DeliveryResult> submit(
            Player worker, CompanyProfile company, FullOrder order, List<DeliveryPart> parts) {
        return record(worker, order, parts)
                .thenApplyAsync(
                        recorded -> {
                            returnSurplus(worker, parts, recorded);
                            return byLine(parts, recorded);
                        },
                        Threading.mainThread(plugin))
                .thenCompose(
                        byLine -> orderData.retrieveFullOrder(order).asFuture().thenApply(refreshed -> {
                            var done = refreshed.isDone();
                            if (done) orderDone(refreshed, company);
                            return new DeliveryResult(byLine, refreshed, done);
                        }));
    }

    private CompletableFuture<List<Integer>> record(Player worker, FullOrder order, List<DeliveryPart> parts) {
        if (parts.isEmpty()) return CompletableFuture.completedFuture(List.of());
        return orderData.submitDeliveries(worker, order, parts).asFuture().exceptionally(err -> {
            plugin.getLogger().log(Level.SEVERE, "Something went wrong", err);
            return List.of();
        });
    }

    /**
     * The amount recorded for a part. Parts the repository did not answer for count as not recorded, so their items
     * go back to the worker.
     *
     * @param recorded recorded amounts per part, may be null or short after a failure
     * @param index    index of the part
     * @return recorded amount
     */
    private static int recorded(List<Integer> recorded, int index) {
        if (recorded == null || index >= recorded.size()) return 0;
        var amount = recorded.get(index);
        return amount == null ? 0 : amount;
    }

    /**
     * Sums the recorded amounts per line.
     *
     * @param parts    offered parts
     * @param recorded recorded amount per part, may be null or short after a failure
     * @return recorded amount per line, in the order of the parts; lines without a recorded item are left out
     */
    static Map<Integer, Integer> byLine(List<DeliveryPart> parts, List<Integer> recorded) {
        Map<Integer, Integer> byLine = new LinkedHashMap<>();
        for (var index = 0; index < parts.size(); index++) {
            var amount = recorded(recorded, index);
            if (amount > 0) byLine.merge(parts.get(index).line(), amount, Integer::sum);
        }
        return byLine;
    }

    /**
     * The items of every part that were not recorded, each with the item of its own part.
     *
     * @param parts    offered parts
     * @param recorded recorded amount per part, may be null or short after a failure
     * @return the parts that have items left, with the amount left
     */
    static List<DeliveryPart> surplus(List<DeliveryPart> parts, List<Integer> recorded) {
        List<DeliveryPart> surplus = new ArrayList<>();
        for (var index = 0; index < parts.size(); index++) {
            var part = parts.get(index);
            var left = part.amount() - recorded(recorded, index);
            if (left > 0) surplus.add(new DeliveryPart(part.line(), part.stack(), left));
        }
        return surplus;
    }

    private void returnSurplus(Player worker, List<DeliveryPart> parts, List<Integer> recorded) {
        for (var part : surplus(parts, recorded)) {
            giveBack(worker, part.stack(), part.amount());
        }
    }

    private void giveBack(Player worker, ItemStack stack, int amount) {
        if (worker.isOnline()) {
            Inventories.give(worker, Inventories.split(stack, amount));
            return;
        }
        orderData.submitItemReturn(worker.getUniqueId(), ReturnKind.WORKER, stack, amount);
    }

    private void orderDone(FullOrder order, CompanyProfile company) {
        completion.complete(order, company).exceptionally(err -> {
            plugin.getLogger().log(Level.SEVERE, "Something went wrong", err);
            return Optional.empty();
        });
    }

    private record CachedMatcher(ItemMatchSettings settings, ItemMatcher matcher) {}
}
