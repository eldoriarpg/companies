/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.ordersettings.SoloSettings;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ReturnedItems;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.PreSoloOrderAcceptEvent;
import de.eldoria.companies.events.order.SoloOrderAbortEvent;
import de.eldoria.companies.events.order.SoloOrderAcceptEvent;
import de.eldoria.companies.events.order.SoloOrderExpiredEvent;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.Replacement;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Orders players work on alone, without a company: claiming, aborting and the release of claims that ran out of time.
 * <p>
 * A player may work alone while solo orders are enabled, they have {@link Permission.Orders#SOLO} and they are in no
 * company, unless company members are allowed as well. Aborting an order or letting one expire starts a cooldown in
 * which the player claims no solo order. Claim, cooldown and limit live in the database, so every server of a network
 * sees the same state.
 * <p>
 * A claim fires {@link PreSoloOrderAcceptEvent}, which can refuse it, and {@link SoloOrderAcceptEvent} once the order
 * is claimed; an abort fires {@link SoloOrderAbortEvent} and an expiry {@link SoloOrderExpiredEvent}. Everything runs
 * on the executor, off the main thread.
 */
public final class SoloOrderService {
    private final AOrderData orderData;
    private final OrderOutcomes outcomes;
    private final Supplier<SoloSettings> settings;
    private final Predicate<Player> inCompany;
    private final Function<UUID, OfflinePlayer> players;
    private final Consumer<Event> events;
    private final Clock clock;
    private final Executor executor;

    /**
     * @param orderData order repository
     * @param outcomes  releases aborted and expired orders and returns their items
     * @param settings  the current solo settings
     * @param inCompany whether a player is a member of a company; called off the main thread
     * @param players   looks up a player by id
     * @param events    fires the events
     * @param clock     clock of the cooldowns
     * @param executor  runs the actions off the main thread
     */
    public SoloOrderService(
            AOrderData orderData,
            OrderOutcomes outcomes,
            Supplier<SoloSettings> settings,
            Predicate<Player> inCompany,
            Function<UUID, OfflinePlayer> players,
            Consumer<Event> events,
            Clock clock,
            Executor executor) {
        this.orderData = orderData;
        this.outcomes = outcomes;
        this.settings = settings;
        this.inCompany = inCompany;
        this.players = players;
        this.events = events;
        this.clock = clock;
        this.executor = executor;
    }

    /**
     * The service of a running plugin, firing the events through its plugin manager.
     *
     * @param plugin        the plugin
     * @param orderData     order repository
     * @param companyData   company repository, telling whether a player is in a company
     * @param outcomes      releases aborted and expired orders
     * @param configuration configuration with the solo settings
     * @return the service
     */
    public static SoloOrderService create(
            Plugin plugin,
            AOrderData orderData,
            ACompanyData companyData,
            OrderOutcomes outcomes,
            Configuration configuration) {
        return new SoloOrderService(
                orderData,
                outcomes,
                () -> configuration.orderSetting().solo(),
                player -> Optional.ofNullable(
                                companyData.retrievePlayerCompany(player).join())
                        .flatMap(company -> company)
                        .isPresent(),
                plugin.getServer()::getOfflinePlayer,
                plugin.getServer().getPluginManager()::callEvent,
                Clock.systemDefaultZone(),
                Threading.VIRTUAL);
    }

    /**
     * The current solo settings.
     *
     * @return settings
     */
    public SoloSettings settings() {
        return settings.get();
    }

    /**
     * Checks whether a player may work on orders alone at all, without looking at limit and cooldown.
     *
     * @param player the player
     * @return why the player may not work alone, or empty when they may
     */
    public CompletableFuture<Optional<CommandFailure>> eligibility(Player player) {
        return CompletableFuture.supplyAsync(() -> refusal(player), executor);
    }

    /**
     * Claims an order for a player who works on it alone.
     *
     * @param player accepting player
     * @param id     id of the order
     * @return the claimed order, or why it was not accepted
     */
    public CompletableFuture<Result<FullOrder>> accept(Player player, int id) {
        return CompletableFuture.supplyAsync(() -> acceptNow(player, id), executor);
    }

    /**
     * Gives an order the player works on alone back. The delivered items go back to the player and the cooldown
     * starts.
     *
     * @param player aborting player
     * @param id     id of the order
     * @return the items returned to the player, or why the order was not aborted
     */
    public CompletableFuture<Result<ReturnedItems>> abort(Player player, int id) {
        return CompletableFuture.supplyAsync(() -> abortNow(player, id), executor);
    }

    /**
     * Loads an order the player works on alone.
     *
     * @param player the worker
     * @param id     id of the order
     * @return the order with its lines, or why the player may not work on it
     */
    public CompletableFuture<Result<FullOrder>> workerOrder(Player player, int id) {
        return CompletableFuture.supplyAsync(
                () -> switch (ownClaim(player.getUniqueId(), id)) {
                    case Result.Refused<SimpleOrder> refused -> Result.<FullOrder>refused(refused.failure());
                    case Result.Done<SimpleOrder> claim -> Result.done(orderData.toFullOrder(claim.value()));
                },
                executor);
    }

    /**
     * The orders a player works on alone.
     *
     * @param player the worker
     * @return the claimed orders with their lines, the oldest claim first
     */
    public CompletableFuture<List<FullOrder>> workerOrders(UUID player) {
        return CompletableFuture.supplyAsync(
                () -> {
                    var orders = orderData
                            .retrieveOrdersBySoloWorker(player, OrderState.CLAIMED, OrderState.CLAIMED)
                            .join();
                    return orders == null ? List.<FullOrder>of() : orderData.toFullOrders(orders);
                },
                executor);
    }

    /**
     * How long a player still may not claim solo orders.
     *
     * @param player the player
     * @return the time left, empty when there is no running cooldown
     */
    public CompletableFuture<Optional<Duration>> cooldownLeft(UUID player) {
        return CompletableFuture.supplyAsync(() -> cooldown(player), executor);
    }

    /**
     * Releases the solo claims whose delivery time passed. The items go back to the workers and their cooldown
     * starts. Only the primary server calls this.
     *
     * @return the released orders
     */
    public CompletableFuture<List<SimpleOrder>> releaseExpired() {
        return CompletableFuture.supplyAsync(this::releaseExpiredNow, executor);
    }

    private Result<FullOrder> acceptNow(Player player, int id) {
        var refused = refusal(player);
        if (refused.isPresent()) return Result.refused(refused.get());
        var solo = settings.get();
        var left = cooldown(player.getUniqueId());
        if (left.isPresent()) {
            return Result.refused(CommandFailure.actionBar(
                    "solo.error.cooldown", Replacement.create("duration", Texts.prettyDuration(left.get()))));
        }
        var active = orderData.retrieveSoloOrderCount(player.getUniqueId()).join();
        if (active == null) return refused("error.couldNotClaim");
        if (active >= solo.maxActive()) {
            return Result.refused(
                    CommandFailure.actionBar("solo.error.limit", Replacement.create("max", solo.maxActive())));
        }

        var found = orderData.retrieveOrderById(id).join();
        if (found == null || found.isEmpty()) return refused("error.unknownOrder");
        var order = found.get();
        if (order.state() != OrderState.UNCLAIMED) return refused("error.orderNotClaimable");
        if (order.owner().equals(player.getUniqueId())) return refused("solo.error.ownOrder");
        var fullOrder = orderData.retrieveFullOrder(order).join();
        if (fullOrder == null) return refused("error.unknownOrder");
        if (solo.exceedsOrderSize(fullOrder.amount())) {
            return Result.refused(
                    CommandFailure.actionBar("solo.error.tooLarge", Replacement.create("max", solo.maxOrderSize())));
        }

        var event = new PreSoloOrderAcceptEvent(fullOrder, player);
        events.accept(event);
        if (event.isCancelled()) return Result.refused(cancelled(event));

        var claimed = orderData
                .submitSoloClaim(player.getUniqueId(), order, solo.maxActive())
                .join();
        if (!Boolean.TRUE.equals(claimed)) return refused("error.couldNotClaim");
        events.accept(new SoloOrderAcceptEvent(order, player));
        return Result.done(claimedOrder(order));
    }

    private Result<ReturnedItems> abortNow(Player player, int id) {
        var claim = ownClaim(player.getUniqueId(), id);
        if (!(claim instanceof Result.Done<SimpleOrder>(var order))) {
            return Result.refused(((Result.Refused<SimpleOrder>) claim).failure());
        }
        var returned = outcomes.abort(order).join();
        if (returned == null || returned.isEmpty()) return refused("error.orderNotActive");
        startCooldown(player.getUniqueId());
        events.accept(new SoloOrderAbortEvent(order, player));
        return Result.done(returned.get());
    }

    private List<SimpleOrder> releaseExpiredNow() {
        var expired = orderData
                .retrieveExpiredSoloOrders(settings.get().deliveryHours())
                .join();
        List<SimpleOrder> released = new ArrayList<>();
        if (expired == null) return released;
        for (var order : expired) {
            var worker = order.soloWorker();
            if (worker.isEmpty()) continue;
            var returned = outcomes.expire(order).join();
            if (returned == null || returned.isEmpty()) continue;
            startCooldown(worker.get());
            events.accept(new SoloOrderExpiredEvent(order, players.apply(worker.get())));
            released.add(order);
        }
        return released;
    }

    /**
     * Loads an order the player works on alone.
     *
     * @param player the worker
     * @param id     id of the order
     * @return the order, or why it is unknown, not claimed alone by the player or no longer claimed
     */
    private Result<SimpleOrder> ownClaim(UUID player, int id) {
        var found = orderData.retrieveOrderById(id).join();
        if (found == null || found.isEmpty()) return refused("error.unknownOrder");
        var order = found.get();
        if (!order.soloWorker().map(player::equals).orElse(false)) return refused("solo.error.notYours");
        if (order.state() != OrderState.CLAIMED) return refused("error.orderNotActive");
        return Result.done(order);
    }

    private Optional<CommandFailure> refusal(Player player) {
        var solo = settings.get();
        if (!solo.enabled()) return Optional.of(CommandFailure.actionBar("solo.error.disabled"));
        if (!player.hasPermission(Permission.Orders.SOLO)) {
            return Optional.of(CommandFailure.actionBar("solo.error.permission"));
        }
        if (!solo.forCompanyMembers() && inCompany.test(player)) {
            return Optional.of(CommandFailure.actionBar("solo.error.companyMember"));
        }
        return Optional.empty();
    }

    private Optional<Duration> cooldown(UUID player) {
        var now = LocalDateTime.now(clock);
        return Optional.ofNullable(orderData.retrieveSoloCooldown(player).join())
                .flatMap(endsAt -> endsAt)
                .filter(now::isBefore)
                .map(endsAt -> Duration.between(now, endsAt));
    }

    private void startCooldown(UUID player) {
        var minutes = settings.get().abortCooldownMinutes();
        if (minutes <= 0) return;
        orderData
                .submitSoloCooldown(player, LocalDateTime.now(clock).plusMinutes(minutes))
                .join();
    }

    private FullOrder claimedOrder(SimpleOrder order) {
        var refreshed = Optional.ofNullable(
                        orderData.retrieveOrderById(order.id()).join())
                .flatMap(found -> found)
                .orElse(order);
        return orderData.retrieveFullOrder(refreshed).join();
    }

    private static CommandFailure cancelled(PreSoloOrderAcceptEvent event) {
        return event.cancelReason()
                .map(reason -> CommandFailure.of("error.cancelledByIntegration", reason))
                .orElseGet(() -> CommandFailure.actionBar("error.cancelledByIntegration"));
    }

    private static <T> Result<T> refused(String key) {
        return Result.refused(CommandFailure.actionBar(key));
    }

    /**
     * The outcome of a solo action.
     *
     * @param <T> what a performed action returns
     */
    public sealed interface Result<T> {
        /**
         * @param value what the action returns
         * @param <T>   type of the value
         * @return a performed action
         */
        static <T> Result<T> done(T value) {
            return new Done<>(value);
        }

        /**
         * @param failure what the player is told
         * @param <T>     type of the value of a performed action
         * @return a refused action
         */
        static <T> Result<T> refused(CommandFailure failure) {
            return new Refused<>(failure);
        }

        /**
         * A performed action.
         *
         * @param value what the action returns
         * @param <T>   type of the value
         */
        record Done<T>(T value) implements Result<T> {}

        /**
         * A refused action, which changed nothing.
         *
         * @param failure what the player is told
         * @param <T>     type of the value of a performed action
         */
        record Refused<T>(CommandFailure failure) implements Result<T> {}
    }
}
