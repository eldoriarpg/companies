/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.commands.suggest.SeriesSources;
import de.eldoria.companies.commands.suggest.SuggestionSources;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.ASeriesData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.series.OrderSeries;
import de.eldoria.companies.data.wrapper.series.SeriesState;
import de.eldoria.companies.orders.OrderCreationService.Wallet;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.services.SeriesService;
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * What owners do with their orders nobody accepted yet: cancelling, extending and raising the price.
 * <p>
 * The owner is given by id and does not need to be online: Vault works for offline players. Every action loads the
 * order fresh and only changes an order that is still unclaimed. Money moves on the main thread, everything else on
 * the executor.
 * <p>
 * A cancelled order refunds its price; the fee goes to the collector. Cancelling a copy of an active repeating order
 * stops the repeating order. A raise charges the increase plus the percentage fee on it and gives the money back when
 * the order could no longer be changed.
 */
public final class OwnerOrderService {
    private static final String UNKNOWN_ORDER = "error.unknownOrder";
    private static final String ALREADY_CLAIMED = "error.orderAlreadyClaimed";

    private final AOrderData orderData;
    private final OrderOutcomes outcomes;
    private final IntFunction<CompletableFuture<Optional<OrderSeries>>> series;
    private final Function<SimpleOrder, CompletableFuture<Boolean>> copyCancelled;
    private final Supplier<OrderSettings> settings;
    private final Wallet wallet;
    private final DoubleConsumer collector;
    private final Consumer<UUID> ordersChanged;
    private final Function<UUID, OfflinePlayer> players;
    private final Executor mainThread;
    private final Executor executor;
    private final Logger logger;

    /**
     * @param orderData     order repository
     * @param outcomes      ends cancelled orders and records their history
     * @param series        loads a repeating order by id
     * @param copyCancelled stops the repeating order of a cancelled copy
     * @param settings      current order settings
     * @param wallet        refunds and charges owners
     * @param collector     takes the fee of a cancelled order
     * @param ordersChanged drops what is cached about the orders of an owner
     * @param players       looks up a player by id
     * @param mainThread    runs the wallet calls on the main thread
     * @param executor      runs the actions
     * @param logger        logs failed changes
     */
    public OwnerOrderService(
            AOrderData orderData,
            OrderOutcomes outcomes,
            IntFunction<CompletableFuture<Optional<OrderSeries>>> series,
            Function<SimpleOrder, CompletableFuture<Boolean>> copyCancelled,
            Supplier<OrderSettings> settings,
            Wallet wallet,
            DoubleConsumer collector,
            Consumer<UUID> ordersChanged,
            Function<UUID, OfflinePlayer> players,
            Executor mainThread,
            Executor executor,
            Logger logger) {
        this.orderData = orderData;
        this.outcomes = outcomes;
        this.series = series;
        this.copyCancelled = copyCancelled;
        this.settings = settings;
        this.wallet = wallet;
        this.collector = collector;
        this.ordersChanged = ordersChanged;
        this.players = players;
        this.mainThread = mainThread;
        this.executor = executor;
        this.logger = logger;
    }

    /**
     * The service of a running plugin, paying through Vault and the collector account.
     *
     * @param plugin        the plugin
     * @param orderData     order repository
     * @param outcomes      ends cancelled orders
     * @param seriesData    repeating order repository
     * @param seriesService repeating orders
     * @param economy       Vault economy
     * @param configuration configuration of the plugin
     * @return the service
     */
    public static OwnerOrderService create(
            Plugin plugin,
            AOrderData orderData,
            OrderOutcomes outcomes,
            ASeriesData seriesData,
            SeriesService seriesService,
            Economy economy,
            Configuration configuration) {
        var collector = new Collector(plugin, economy, configuration);
        return new OwnerOrderService(
                orderData,
                outcomes,
                seriesData::retrieveSeries,
                seriesService::onCopyCancelled,
                configuration::orderSetting,
                Wallet.of(economy),
                collector::collect,
                owner -> {
                    SuggestionSources.current()
                            .ifPresent(sources -> sources.ownOrders().invalidate(owner));
                    SeriesSources.invalidate(owner);
                },
                plugin.getServer()::getOfflinePlayer,
                Threading.mainThread(plugin),
                Threading.VIRTUAL,
                plugin.getLogger());
    }

    /**
     * Checks whether the owner could cancel an order, without cancelling it.
     *
     * @param owner id of the owner
     * @param id    id of the order
     * @return the order and the active repeating order it is a copy of, or why it can not be cancelled
     */
    public CompletableFuture<ActionResult<Cancellable>> cancellable(UUID owner, int id) {
        return run(() -> switch (ownOpenOrder(owner, id)) {
            case ActionResult.Refused<SimpleOrder>(var failure) -> ActionResult.refused(failure);
            case ActionResult.Done<SimpleOrder>(var order) ->
                ActionResult.done(new Cancellable(order, activeSeries(order)));
        });
    }

    private Optional<OrderSeries> activeSeries(SimpleOrder order) {
        var seriesId = order.seriesId();
        if (seriesId.isEmpty()) return Optional.empty();
        return series.apply(seriesId.getAsInt()).join().filter(candidate -> candidate.state() == SeriesState.ACTIVE);
    }

    /**
     * Cancels an unaccepted order. The price goes back to the owner and the fee to the collector; a copy of a
     * repeating order stops the repeating order.
     *
     * @param owner id of the owner
     * @param id    id of the order
     * @return the cancelled order, or why it was not cancelled
     */
    public CompletableFuture<ActionResult<FullOrder>> cancel(UUID owner, int id) {
        return run(() -> switch (ownOpenOrder(owner, id)) {
            case ActionResult.Refused<SimpleOrder>(var failure) -> ActionResult.refused(failure);
            case ActionResult.Done<SimpleOrder>(var order) -> cancelNow(owner, order);
        });
    }

    private ActionResult<FullOrder> cancelNow(UUID owner, SimpleOrder order) {
        var fullOrder = orderData.retrieveFullOrder(order).join();
        if (fullOrder == null) return ActionResult.refusedActionBar(UNKNOWN_ORDER);
        if (!Boolean.TRUE.equals(outcomes.cancel(fullOrder).join())) return ActionResult.refusedChat(ALREADY_CLAIMED);
        runOnMain(() -> wallet.deposit(players.apply(owner), fullOrder.price()));
        collector.accept(fullOrder.fee());
        if (fullOrder.seriesId().isPresent()) copyCancelled.apply(fullOrder).join();
        ordersChanged.accept(owner);
        return ActionResult.done(fullOrder);
    }

    private ActionResult<SimpleOrder> ownOpenOrder(UUID owner, int id) {
        var optOrder = loadOrder(id);
        if (optOrder.isEmpty()) return ActionResult.refusedActionBar(UNKNOWN_ORDER);
        var order = optOrder.get();
        if (!order.owner().equals(owner)) return ActionResult.refusedChat("error.notYourOrder");
        if (order.state() != OrderState.UNCLAIMED) return ActionResult.refusedChat(ALREADY_CLAIMED);
        return ActionResult.done(order);
    }

    /**
     * Restarts the purge timer of an unaccepted order, up to {@code maxExtensions} times per order.
     *
     * @param owner id of the owner
     * @param id    id of the order
     * @return the order with the extensions left and the time until it is purged, or why it was not extended
     */
    public CompletableFuture<ActionResult<Extended>> extend(UUID owner, int id) {
        return run(() -> {
            var current = settings.get();
            return switch (unclaimed(owner, id)) {
                case ActionResult.Refused<SimpleOrder>(var failure) -> ActionResult.refused(failure);
                case ActionResult.Done<SimpleOrder>(var order) -> extendNow(order, current);
            };
        });
    }

    private ActionResult<Extended> extendNow(SimpleOrder order, OrderSettings current) {
        var maxExtensions = current.maxExtensions();
        if (order.extensionsLeft(maxExtensions) == 0
                || !Boolean.TRUE.equals(
                        orderData.submitOrderExtension(order, maxExtensions).join())) {
            return ActionResult.refusedChat("order.extend.noneLeft");
        }
        return ActionResult.done(new Extended(
                order, order.extensionsLeft(maxExtensions) - 1, Duration.ofHours(current.maxUnclaimedHours())));
    }

    /**
     * What raising the price of an unaccepted order would cost, without raising it.
     *
     * @param owner    id of the owner
     * @param id       id of the order
     * @param increase increase of the price, rounded down to two decimals
     * @return the order with the increase and the extra fee, or why the price can not be raised
     */
    public CompletableFuture<ActionResult<Raise>> raiseQuote(UUID owner, int id, double increase) {
        return run(() -> quote(owner, id, increase));
    }

    /**
     * Raises the price of an unaccepted order. The owner pays the increase plus the percentage fee on it; the money
     * goes back when the order could no longer be changed.
     *
     * @param owner    id of the owner
     * @param id       id of the order
     * @param increase increase of the price, rounded down to two decimals
     * @return the order before the raise with the increase and the extra fee, or why the price was not raised
     */
    public CompletableFuture<ActionResult<Raise>> raise(UUID owner, int id, double increase) {
        return run(() -> switch (quote(owner, id, increase)) {
            case ActionResult.Refused<Raise> refused -> refused;
            case ActionResult.Done<Raise>(var raise) -> raiseNow(owner, raise);
        });
    }

    private ActionResult<Raise> quote(UUID owner, int id, double amount) {
        var increase = Money.floor(amount);
        if (!(increase > 0)) return ActionResult.refusedChat("error.invalidNumber");
        return switch (unclaimed(owner, id)) {
            case ActionResult.Refused<SimpleOrder>(var failure) -> ActionResult.refused(failure);
            case ActionResult.Done<SimpleOrder>(var order) -> quote(order, increase);
        };
    }

    private ActionResult<Raise> quote(SimpleOrder order, double increase) {
        var fullOrder = orderData.retrieveFullOrder(order).join();
        if (fullOrder == null) return ActionResult.refusedActionBar(UNKNOWN_ORDER);
        var fee = settings.get().fee().raise(fullOrder.fee(), increase);
        return ActionResult.done(new Raise(fullOrder, increase, fee));
    }

    private ActionResult<Raise> raiseNow(UUID owner, Raise raise) {
        var player = players.apply(owner);
        var total = raise.total();
        if (!onMain(() -> wallet.withdraw(player, total))) {
            return ActionResult.refusedChat(
                    "error.insufficientCurrency",
                    Replacement.create("currency", wallet.currencyName()),
                    Replacement.create("amount", wallet.format(total)));
        }
        var raised = orderData
                .submitPriceRaise(raise.order(), increases(raise.order(), raise.increase()), raise.fee())
                .asFuture()
                .exceptionally(err -> {
                    logger.log(Level.SEVERE, "Could not raise the price of an order", err);
                    return false;
                })
                .join();
        if (!Boolean.TRUE.equals(raised)) {
            runOnMain(() -> wallet.deposit(player, total));
            return ActionResult.refusedChat(ALREADY_CLAIMED);
        }
        return ActionResult.done(raise);
    }

    private static Map<Integer, Double> increases(FullOrder order, double increase) {
        var contents = order.contents();
        var shares = PriceRaise.split(contents.stream().map(OrderContent::price).toList(), increase);
        Map<Integer, Double> increases = new HashMap<>();
        for (var i = 0; i < contents.size(); i++) {
            increases.merge(contents.get(i).line(), shares.get(i), Double::sum);
        }
        return increases;
    }

    private ActionResult<SimpleOrder> unclaimed(UUID owner, int id) {
        var optOrder = loadOrder(id);
        if (optOrder.isEmpty() || !optOrder.get().owner().equals(owner)) {
            return ActionResult.refusedActionBar(UNKNOWN_ORDER);
        }
        if (optOrder.get().state() != OrderState.UNCLAIMED) return ActionResult.refusedChat(ALREADY_CLAIMED);
        return ActionResult.done(optOrder.get());
    }

    private Optional<SimpleOrder> loadOrder(int id) {
        return orderData
                .retrieveOrderById(id)
                .asFuture()
                .exceptionally(err -> {
                    logger.log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .join();
    }

    private void runOnMain(Runnable task) {
        CompletableFuture.runAsync(task, mainThread).join();
    }

    private <T> T onMain(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, mainThread).join();
    }

    private <T> CompletableFuture<ActionResult<T>> run(Supplier<ActionResult<T>> action) {
        return CompletableFuture.supplyAsync(action, executor);
    }

    /**
     * An order its owner may cancel.
     *
     * @param order        the order
     * @param activeSeries the active repeating order the order is a copy of, which the cancel stops
     */
    public record Cancellable(SimpleOrder order, Optional<OrderSeries> activeSeries) {}

    /**
     * An extended order.
     *
     * @param order    the order before the extension
     * @param left     extensions left
     * @param refundIn time until an unaccepted order is purged and refunded, counted from now
     */
    public record Extended(SimpleOrder order, int left, Duration refundIn) {}

    /**
     * A raise of the price of an order.
     *
     * @param order    the order before the raise
     * @param increase increase of the price
     * @param fee      fee charged on the increase
     */
    public record Raise(FullOrder order, double increase, double fee) {
        /**
         * @return what the owner pays: the increase and its fee
         */
        public double total() {
            return increase + fee;
        }

        /**
         * @return the price of the order after the raise
         */
        public double newPrice() {
            return order.price() + increase;
        }
    }
}
