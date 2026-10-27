/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.IDeliveredStack;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.events.order.OrderDeliveryEvent;
import de.eldoria.companies.events.order.SoloOrderDeliveryEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent.ReturnedStack;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.util.Inventories;
import de.eldoria.companies.util.Threading;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Delivers items of workers to the orders of their company, and of players to the orders they work on alone.
 * <p>
 * A delivery takes the matching items on the main thread, records them as delivered stacks in one transaction that
 * caps every line at what it still misses, and hands the surplus of every part back with that part's own item: into
 * the inventory, dropped when it is full, or into the item returns when the worker went offline. A delivery that
 * recorded at least one item fires {@link OrderDeliveryEvent}, or {@link SoloOrderDeliveryEvent} for a solo order, with
 * the refreshed order. When the order is complete afterwards, {@link OrderCompletionService} completes it.
 */
public final class DeliveryService {
    private final Plugin plugin;
    private final AOrderData orderData;
    private final OrderCompletionService completion;
    private final ItemReturnService itemReturns;
    private final Supplier<ItemMatchSettings> matching;
    private volatile CachedMatcher cachedMatcher;

    /**
     * @param plugin      the plugin, which fires the events
     * @param orderData   order repository
     * @param completion  completes orders once everything was delivered
     * @param itemReturns stores the surplus of workers who went offline
     * @param matching    the current item matching settings
     */
    public DeliveryService(
            Plugin plugin,
            AOrderData orderData,
            OrderCompletionService completion,
            ItemReturnService itemReturns,
            Supplier<ItemMatchSettings> matching) {
        this.plugin = plugin;
        this.orderData = orderData;
        this.completion = completion;
        this.itemReturns = itemReturns;
        this.matching = matching;
    }

    /**
     * The matcher of the current matching settings. It is built again once the settings were reloaded.
     *
     * @return the matcher
     */
    public ItemMatcher matcher() {
        var settings = matching.get();
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
        return submit(worker, company, order, lineParts(worker, order, line, amount));
    }

    /**
     * Delivers items to one line of an order the worker works on alone. Has to be called on the main thread.
     *
     * @param worker delivering player, who claimed the order alone
     * @param order  order as it was loaded
     * @param line   line of the order
     * @param amount the most items to deliver; capped by what the line misses
     * @return what was delivered
     */
    public CompletableFuture<DeliveryResult> deliverSolo(Player worker, FullOrder order, int line, int amount) {
        return submitSolo(worker, order, lineParts(worker, order, line, amount));
    }

    private List<DeliveryPart> lineParts(Player worker, FullOrder order, int line, int amount) {
        var matcher = matcher();
        var slots = ItemSources.collect(worker);
        return order.line(line)
                .map(content -> ItemSources.take(
                        slots,
                        content.line(),
                        content.stack(),
                        content.criteria().orElse(null),
                        matcher,
                        Math.min(amount, content.missing())))
                .orElse(List.of());
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
        return submit(worker, company, order, allParts(worker, order));
    }

    /**
     * Delivers every matching item the worker carries to all lines of an order the worker works on alone. Has to be
     * called on the main thread.
     *
     * @param worker delivering player, who claimed the order alone
     * @param order  order as it was loaded
     * @return what was delivered
     */
    public CompletableFuture<DeliveryResult> deliverAllSolo(Player worker, FullOrder order) {
        return submitSolo(worker, order, allParts(worker, order));
    }

    private List<DeliveryPart> allParts(Player worker, FullOrder order) {
        var matcher = matcher();
        return ItemSources.fill(ItemSources.collect(worker), order.contents(), matcher);
    }

    /**
     * Records items taken from a worker as a delivery, hands back the surplus and completes the order once it is
     * done. Has to be called on the main thread.
     *
     * @param worker  delivering player
     * @param company company of the worker, which claimed the order
     * @param order   order as it was loaded
     * @param parts   items taken from the worker
     * @return what was delivered
     */
    CompletableFuture<DeliveryResult> submit(
            Player worker, CompanyProfile company, FullOrder order, List<DeliveryPart> parts) {
        return submit(worker, order, parts, new Claim() {
            @Override
            public Event delivered(FullOrder refreshed, List<IDeliveredStack> stacks) {
                return new OrderDeliveryEvent(refreshed, company, worker, stacks);
            }

            @Override
            public CompletableFuture<?> complete(FullOrder refreshed) {
                return completion.complete(refreshed, company);
            }
        });
    }

    /**
     * Records items taken from a worker as a delivery to an order the worker works on alone, hands back the surplus
     * and completes the order once it is done. Has to be called on the main thread.
     *
     * @param worker delivering player, who claimed the order alone
     * @param order  order as it was loaded
     * @param parts  items taken from the worker
     * @return what was delivered
     */
    CompletableFuture<DeliveryResult> submitSolo(Player worker, FullOrder order, List<DeliveryPart> parts) {
        return submit(worker, order, parts, new Claim() {
            @Override
            public Event delivered(FullOrder refreshed, List<IDeliveredStack> stacks) {
                return new SoloOrderDeliveryEvent(refreshed, worker, stacks);
            }

            @Override
            public CompletableFuture<?> complete(FullOrder refreshed) {
                return completion.completeSolo(refreshed);
            }
        });
    }

    private CompletableFuture<DeliveryResult> submit(
            Player worker, FullOrder order, List<DeliveryPart> parts, Claim claim) {
        return record(worker, order, parts)
                .thenApplyAsync(
                        recorded -> {
                            returnSurplus(worker, order, parts, recorded);
                            return new Recorded(byLine(parts, recorded), delivered(worker, parts, recorded));
                        },
                        Threading.mainThread(plugin))
                .thenCompose(recorded -> orderData
                        .retrieveFullOrder(order)
                        .asFuture()
                        .thenApply(refreshed -> {
                            if (!recorded.stacks().isEmpty()) {
                                plugin.getServer()
                                        .getPluginManager()
                                        .callEvent(claim.delivered(refreshed, recorded.stacks()));
                            }
                            var done = refreshed.isDone();
                            if (done) orderDone(refreshed, claim);
                            return new DeliveryResult(recorded.byLine(), refreshed, done);
                        }));
    }

    /**
     * The stacks a delivery recorded, each with the item of its part.
     *
     * @param worker   delivering player
     * @param parts    offered parts
     * @param recorded recorded amount per part, may be null or short after a failure
     * @return the recorded stacks, leaving out parts without a recorded item
     */
    static List<IDeliveredStack> delivered(Player worker, List<DeliveryPart> parts, List<Integer> recorded) {
        List<IDeliveredStack> stacks = new ArrayList<>();
        for (var index = 0; index < parts.size(); index++) {
            var amount = recorded(recorded, index);
            if (amount <= 0) continue;
            stacks.add(
                    new Delivered(worker.getUniqueId(), parts.get(index).stack().asOne(), amount));
        }
        return stacks;
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

    private void returnSurplus(Player worker, FullOrder order, List<DeliveryPart> parts, List<Integer> recorded) {
        var surplus = surplus(parts, recorded);
        if (worker.isOnline()) {
            surplus.forEach(part -> Inventories.give(worker, Inventories.split(part.stack(), part.amount())));
            return;
        }
        var stacks = surplus.stream()
                .map(part -> new ReturnedStack(part.stack(), part.amount()))
                .toList();
        itemReturns.storeAll(
                worker.getUniqueId(), ReturnKind.WORKER, stacks, ItemsReturnedEvent.Reason.DELIVERY_OVERFLOW, order);
    }

    private void orderDone(FullOrder order, Claim claim) {
        claim.complete(order).exceptionally(err -> {
            plugin.getLogger().log(Level.SEVERE, "Something went wrong", err);
            return null;
        });
    }

    /**
     * What a delivery does that depends on who claimed the order.
     */
    private interface Claim {
        /**
         * @param refreshed the order after the delivery
         * @param stacks    the recorded stacks
         * @return the event announcing the delivery
         */
        Event delivered(FullOrder refreshed, List<IDeliveredStack> stacks);

        /**
         * Completes the order once everything was delivered.
         *
         * @param refreshed the completely delivered order
         * @return the running completion
         */
        CompletableFuture<?> complete(FullOrder refreshed);
    }

    private record CachedMatcher(ItemMatchSettings settings, ItemMatcher matcher) {}

    private record Recorded(Map<Integer, Integer> byLine, List<IDeliveredStack> stacks) {}

    private record Delivered(UUID worker, ItemStack stack, int amount) implements IDeliveredStack {
        @Override
        public ItemStack stack() {
            return stack.clone();
        }
    }
}
