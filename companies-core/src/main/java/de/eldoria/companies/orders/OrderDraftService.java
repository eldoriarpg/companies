/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.IOrderDraft;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.criteria.CriteriaRules;
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Times;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Holds the order drafts of the players and performs the builder actions on them.
 * <p>
 * A draft is kept for {@link #LIFETIME} after it was last used. Actions on a draft that does not exist any more are
 * answered with {@code order.create.draftExpired} by the callers. Rule violations end an action with a
 * {@link CommandFailure}; asynchronous actions complete exceptionally with one.
 * <p>
 * Finishing a draft takes it out of the cache before anything is charged, so only the first of several clicks on done
 * goes on. A draft that could not be turned into an order is put back, so the player can fix it.
 */
public final class OrderDraftService {
    /**
     * How long a draft is kept after it was last used.
     */
    public static final Duration LIFETIME = Duration.ofMinutes(15);

    /**
     * Names of orders have to stay below this length.
     */
    public static final int MAX_NAME_LENGTH = 32;

    private final Plugin plugin;
    private final AOrderData orderData;
    private final Configuration configuration;
    private final OrderLimits limits;
    private final Cache<UUID, OrderBuilder> drafts =
            CacheBuilder.newBuilder().expireAfterAccess(LIFETIME).build();
    private volatile Optional<SettingsMatcher> matcher = Optional.empty();

    /**
     * @param plugin        the plugin
     * @param orderData     order repository
     * @param configuration configuration of the plugin
     */
    public OrderDraftService(Plugin plugin, AOrderData orderData, Configuration configuration) {
        this.plugin = plugin;
        this.orderData = orderData;
        this.configuration = configuration;
        this.limits = new OrderLimits(orderData, () -> configuration.userSettings());
    }

    /**
     * @return the limits of active orders per player
     */
    public OrderLimits limits() {
        return limits;
    }

    /**
     * @param player id of the player
     * @return the draft of the player, empty when it expired or was never started
     */
    public Optional<OrderBuilder> draft(UUID player) {
        return Optional.ofNullable(drafts.getIfPresent(player));
    }

    /**
     * Starts a new draft, replacing a running one, unless the player reached the order limit.
     *
     * @param player player creating the order
     * @param name   name of the order
     * @return the draft, or a {@link CommandFailure} when the limit is reached
     */
    public CompletableFuture<OrderBuilder> start(Player player, String name) {
        return limits.usage(player).asFuture().thenApply(usage -> {
            if (usage.reached()) throw CommandFailure.of("order.create.error.limitReached");
            var draft = new OrderBuilder(player.getUniqueId(), name);
            drafts.put(player.getUniqueId(), draft);
            return draft;
        });
    }

    /**
     * @param draft draft of the player
     * @param name  new name of the order
     */
    public void rename(OrderBuilder draft, String name) {
        Checks.shorterThan(name, MAX_NAME_LENGTH);
        draft.name(name);
    }

    /**
     * Adds the item in the main hand of a player. Its data decides whether it becomes an exact line.
     *
     * @param draft     draft of the player
     * @param player    player holding the item
     * @param amount    number of items
     * @param unitPrice price of one item
     * @return number of the line, starting at 1
     */
    public int addHand(OrderBuilder draft, Player player, int amount, double unitPrice) {
        var hand = player.getInventory().getItemInMainHand();
        if (hand.isEmpty()) throw CommandFailure.actionBar("order.draft.error.emptyHand");
        return add(draft, hand, amount, unitPrice);
    }

    /**
     * Adds an item. Its data decides whether it becomes an exact line. The amount is capped by the item limit.
     *
     * @param draft     draft of the player
     * @param item      requested item
     * @param amount    number of items
     * @param unitPrice price of one item
     * @return number of the line, starting at 1
     */
    public int add(OrderBuilder draft, ItemStack item, int amount, double unitPrice) {
        var requested = request(item).orElseThrow(() -> CommandFailure.actionBar("order.draft.error.notOrderable"));
        var settings = configuration.orderSetting();
        var merged = draft.similarLine(requested.template(), requested.exact()).isPresent();
        if (!merged && draft.lineCount() >= settings.maxMaterials()) {
            throw CommandFailure.actionBar("order.create.error.materialLimit");
        }
        if (draft.amount() >= settings.maxItems()) throw CommandFailure.actionBar("order.create.error.itemLimit");
        var capped = Math.min(Math.max(amount, 1), settings.maxItems() - draft.amount());
        return draft.add(requested.template(), requested.exact(), capped, Math.max(0.0, unitPrice));
    }

    /**
     * Adds a criteria line, or adds the amount to a line with the same criteria. The criteria have to pass
     * {@link CriteriaRules}; the amount is capped by the item limit.
     *
     * @param draft     draft of the player
     * @param criteria  what the line accepts
     * @param amount    number of items
     * @param unitPrice price of one item
     * @return number of the line, starting at 1
     */
    public int add(OrderBuilder draft, ItemCriteria criteria, int amount, double unitPrice) {
        var problem = CriteriaRules.problem(criteria);
        if (problem.isPresent()) throw problem.get();
        var line = new OrderBuilder.DraftLine(criteria.template(), false, 1, 0, criteria);
        var settings = configuration.orderSetting();
        var merged = draft.similarLine(line).isPresent();
        if (!merged && draft.lineCount() >= settings.maxMaterials()) {
            throw CommandFailure.actionBar("order.create.error.materialLimit");
        }
        if (draft.amount() >= settings.maxItems()) throw CommandFailure.actionBar("order.create.error.itemLimit");
        var capped = Math.min(Math.max(amount, 1), settings.maxItems() - draft.amount());
        return draft.add(criteria, capped, Math.max(0.0, unitPrice));
    }

    /**
     * @param draft draft of the player
     * @param line  number of the line, starting at 1
     */
    public void remove(OrderBuilder draft, int line) {
        if (!draft.remove(line)) throw unknownLine();
    }

    /**
     * Changes the amount of a line, keeping its unit price. An amount of zero removes the line. The amount is capped
     * by the item limit.
     *
     * @param draft  draft of the player
     * @param line   number of the line, starting at 1
     * @param amount new amount
     */
    public void amount(OrderBuilder draft, int line, int amount) {
        if (draft.line(line).isEmpty()) throw unknownLine();
        if (amount <= 0) {
            draft.remove(line);
            return;
        }
        var capped = Math.min(amount, configuration.orderSetting().maxItems() - draft.amountWithout(line));
        if (capped < 1) throw CommandFailure.actionBar("order.create.error.itemLimit");
        draft.amount(line, capped);
    }

    /**
     * @param draft     draft of the player
     * @param line      number of the line, starting at 1
     * @param unitPrice new price of one item
     */
    public void unitPrice(OrderBuilder draft, int line, double unitPrice) {
        if (!draft.unitPrice(line, Math.max(0.0, unitPrice))) throw unknownLine();
    }

    /**
     * Sets the unit price of a material line to the market average.
     *
     * @param draft draft of the player
     * @param line  number of the line, starting at 1
     */
    public void marketPrice(OrderBuilder draft, int line) {
        var draftLine = draft.line(line).orElseThrow(OrderDraftService::unknownLine);
        var price = OrderBuilder.offeredMarketPrice(
                        draftLine, orderData.getMaterialPrice(draftLine.materialIdentifier()))
                .orElseThrow(() -> CommandFailure.actionBar("order.draft.error.noMarketPrice"));
        draft.unitPrice(line, Money.offered(price.price()));
    }

    /**
     * Makes a draft repeat once it is finished.
     *
     * @param draft      draft of the player
     * @param player     player owning the draft
     * @param repetition how the order repeats
     * @throws CommandFailure when the player may not make orders repeat or the repetition breaks the settings
     */
    public void repeat(OrderBuilder draft, Player player, Repetition repetition) {
        if (!player.hasPermission(Permission.Orders.REPEAT)) {
            throw CommandFailure.actionBar("error.permission.repeatOrder");
        }
        repetition.validate(configuration.orderSetting().repeating(), Times.today());
        draft.repetition(Optional.of(repetition));
    }

    /**
     * Makes a draft a one-off order again.
     *
     * @param draft draft of the player
     */
    public void once(OrderBuilder draft) {
        draft.repetition(Optional.empty());
    }

    /**
     * Drops the draft of a player.
     *
     * @param player id of the player
     */
    public void cancel(UUID player) {
        drafts.invalidate(player);
    }

    /**
     * Creates the order of the player's draft through the order creation, which charges the player. A repeating draft
     * starts a repeating order instead, whose first copy is the created order.
     *
     * @param player   player owning the draft
     * @param creation creates the order
     * @param series   starts repeating orders
     * @return the created order, or a {@link CommandFailure} when it was not created
     */
    public CompletableFuture<OrderCreationService.Created> done(
            Player player, OrderCreationService creation, SeriesStarter series) {
        var draft = drafts.getIfPresent(player.getUniqueId());
        if (draft == null) return CompletableFuture.failedFuture(CommandFailure.of("order.create.draftExpired"));
        if (draft.lineCount() == 0)
            return CompletableFuture.failedFuture(CommandFailure.of("order.create.error.empty"));
        var order = drafts.asMap().remove(player.getUniqueId());
        if (order == null) return CompletableFuture.failedFuture(CommandFailure.of("order.create.draftExpired"));

        var repetition = order.repetition();
        var finished = repetition.isPresent()
                ? series.start(player, order, repetition.get())
                : creation.create(player, toDraft(order), OrderSource.PLAYER, true, true)
                        .thenApply(outcome -> switch (outcome) {
                            case OrderCreationService.Created done -> done;
                            case OrderCreationService.Rejected rejected -> throw rejected.failure();
                        });
        return finished.whenComplete((created, err) -> {
            if (err != null) drafts.asMap().putIfAbsent(player.getUniqueId(), order);
        });
    }

    /**
     * The draft of a player as listeners of the order creation see it.
     *
     * @param order draft of the player
     * @return a copy of the draft
     */
    static IOrderDraft toDraft(OrderBuilder order) {
        return toDraft(order.name(), order.lines());
    }

    /**
     * Lines of an order as listeners of the order creation see them.
     *
     * @param name  name of the order
     * @param lines lines of the order
     * @return a new draft
     */
    public static IOrderDraft toDraft(String name, List<OrderBuilder.DraftLine> lines) {
        var draft = IOrderDraft.of(name);
        for (var line : lines) {
            var criteria = line.criteria();
            if (criteria != null) {
                draft.addLine(criteria, line.amount(), line.unitPrice());
            } else if (line.exact()) {
                draft.addLine(line.template(), line.amount(), line.unitPrice());
            } else {
                draft.addLine(line.template().getType(), line.amount(), line.unitPrice());
            }
        }
        return draft;
    }

    /**
     * Turns an item into what an order line requests.
     *
     * @param item item to order
     * @return the template and whether it is exact, empty when the item can not be ordered
     */
    public Optional<RequestedItem> request(ItemStack item) {
        var itemMatcher = matcher();
        return itemMatcher.template(item).map(template -> new RequestedItem(template, itemMatcher.isExact(template)));
    }

    private ItemMatcher matcher() {
        var settings = configuration.orderSetting().matching();
        var current = matcher.filter(cached -> cached.settings() == settings);
        if (current.isPresent()) return current.get().matcher();
        var created = new SettingsMatcher(settings, ItemMatcher.of(settings, plugin.getLogger()));
        matcher = Optional.of(created);
        return created.matcher();
    }

    private static CommandFailure unknownLine() {
        return CommandFailure.actionBar("order.draft.error.unknownLine");
    }

    /**
     * What an order line requests for an item.
     *
     * @param template requested item with an amount of one
     * @param exact    whether the data of the item is requested as well
     */
    public record RequestedItem(ItemStack template, boolean exact) {}

    /**
     * Starts a repeating order from a finished draft.
     */
    @FunctionalInterface
    public interface SeriesStarter {
        /**
         * @param owner      player owning the draft
         * @param draft      the finished draft
         * @param repetition how the order repeats
         * @return the first copy, or a {@link CommandFailure} when the repeating order was not started
         */
        CompletableFuture<OrderCreationService.Created> start(Player owner, OrderBuilder draft, Repetition repetition);
    }

    private record SettingsMatcher(ItemMatchSettings settings, ItemMatcher matcher) {}
}
