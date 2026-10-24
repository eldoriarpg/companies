/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.api.write.RejectReason;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.IOrderDraft;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.ordersettings.RepeatingSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.ASeriesData;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.series.OrderSeries;
import de.eldoria.companies.data.wrapper.series.SeriesLine;
import de.eldoria.companies.data.wrapper.series.SeriesState;
import de.eldoria.companies.data.wrapper.series.SeriesStopReason;
import de.eldoria.companies.data.wrapper.series.SeriesWait;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderCreationService.Created;
import de.eldoria.companies.orders.OrderCreationService.Outcome;
import de.eldoria.companies.orders.OrderCreationService.Rejected;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.OrderLimits;
import de.eldoria.companies.orders.Repetition;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.messages.Replacement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Starts, posts, stops and resumes repeating orders.
 * <p>
 * A due series is handled in this order: a series past its end finishes; a series whose previous copy still waits for
 * a company waits silently; a series whose owner reached the order limit waits and tells the owner once; otherwise the
 * copy is claimed, so only one caller posts it, and created with {@link OrderSource#REPEATING}, charging price and fee
 * without the limits of a player's order. The schedule keeps its original times; intervals missed while the series
 * waited are skipped, not caught up.
 * <p>
 * A copy the owner can not pay stops the series. A copy cancelled by an integration or refused as invalid is skipped and
 * its interval counts. A copy that could not be saved releases its claim, so it is posted at the next check. A series
 * finishes right after its last copy.
 * <p>
 * Due copies are posted by the primary node only; starting, stopping and resuming work on every node, because every
 * change is a conditional update in the database.
 */
public final class SeriesService implements Listener {
    /**
     * Most series handled by one check.
     */
    static final int BATCH = 100;

    private final ASeriesData data;
    private final Copies copies;
    private final Function<UUID, CompletableFuture<Integer>> activeOrders;
    private final ToIntFunction<Player> limits;
    private final Notices notices;
    private final Supplier<RepeatingSettings> settings;
    private final Clock clock;
    private final Logger logger;

    /**
     * @param data         repeating order repository
     * @param copies       creates the copies
     * @param activeOrders counts the active orders of a player
     * @param limits       the order limit of an online player
     * @param notices      tells the owners
     * @param settings     current repeating order settings
     * @param clock        clock giving the current time
     * @param logger       logs failed checks
     */
    public SeriesService(
            ASeriesData data,
            Copies copies,
            Function<UUID, CompletableFuture<Integer>> activeOrders,
            ToIntFunction<Player> limits,
            Notices notices,
            Supplier<RepeatingSettings> settings,
            Clock clock,
            Logger logger) {
        this.data = data;
        this.copies = copies;
        this.activeOrders = activeOrders;
        this.limits = limits;
        this.notices = notices;
        this.settings = settings;
        this.clock = clock;
        this.logger = logger;
    }

    /**
     * The service of a running plugin: copies are created through the order creation and the owners are notified
     * through the inbox.
     *
     * @param plugin        the plugin
     * @param data          repeating order repository
     * @param orderData     order repository counting active orders
     * @param creation      creates the copies
     * @param limits        limits of active orders per player
     * @param money         formats amounts of money
     * @param notifications sends the notifications
     * @param settings      current repeating order settings
     * @return the service
     */
    public static SeriesService create(
            Plugin plugin,
            ASeriesData data,
            AOrderData orderData,
            OrderCreationService creation,
            OrderLimits limits,
            DoubleFunction<String> money,
            NotificationService notifications,
            Supplier<RepeatingSettings> settings) {
        var server = plugin.getServer();
        Copies copies = (owner, draft) ->
                creation.create(server.getOfflinePlayer(owner), draft, OrderSource.REPEATING, true, false);
        Function<UUID, CompletableFuture<Integer>> active = owner -> orderData
                .retrievePlayerOrderCount(server.getOfflinePlayer(owner))
                .asFuture();
        return new SeriesService(
                data,
                copies,
                active,
                limits::limit,
                new InboxNotices(notifications, money),
                settings,
                Times.UTC,
                plugin.getLogger());
    }

    /**
     * Checks for due copies every {@link RepeatingSettings#checkMinutes()} minutes.
     *
     * @param executor executor running the checks
     */
    public void schedule(ScheduledExecutorService executor) {
        var minutes = settings.get().checkMinutes();
        executor.scheduleWithFixedDelay(this::runDue, 1L, minutes, TimeUnit.MINUTES);
    }

    /**
     * Handles every due series once. Does nothing while repeating orders are disabled.
     *
     * @return what was decided for each due series
     */
    public List<Decision> runDue() {
        if (!settings.get().enabled()) return List.of();
        var now = now();
        List<OrderSeries> due;
        try {
            due = data.retrieveDue(now, BATCH).join();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not load the due repeating orders", e);
            return List.of();
        }
        var decisions = new ArrayList<Decision>();
        for (var series : due) {
            try {
                decisions.add(postDue(series, now).join());
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not post a copy of repeating order " + series.id(), e);
                decisions.add(Decision.FAILED);
            }
        }
        return decisions;
    }

    /**
     * Handles one due series.
     *
     * @param series series as read by {@link ASeriesData#retrieveDue(LocalDateTime, int)}
     * @param now    current time
     * @return what was decided
     */
    public CompletableFuture<Decision> postDue(OrderSeries series, LocalDateTime now) {
        if (series.endReached(now)) return finish(series, now, true).thenApply(ignored -> Decision.FINISHED);
        return data.retrieveCurrentOrderState(series).thenCompose(state -> {
            if (state.filter(OrderState.UNCLAIMED::equals).isPresent()) {
                return data.submitWaiting(series.id(), SeriesWait.PREVIOUS_OPEN, now)
                        .thenApply(ignored -> Decision.WAITING_PREVIOUS);
            }
            return activeOrders.apply(series.owner()).thenCompose(active -> {
                if (active >= series.orderLimit()) return waitForLimit(series, now);
                var nextDue = series.interval().nextAfter(series.nextDue(), now);
                return claimAndPost(series, nextDue, now, true).thenApply(Posting::decision);
            });
        });
    }

    /**
     * Starts a repeating order from a finished draft and posts its first copy at once. A first copy that is not
     * created stops the series again: one the owner can not pay with {@link SeriesStopReason#CANNOT_PAY}, any other
     * with {@link SeriesStopReason#INVALID}.
     *
     * @param owner      player owning the draft, online
     * @param draft      the finished draft
     * @param repetition how the order repeats
     * @return the first copy, or a {@link CommandFailure} when the repeating order was not started
     */
    public CompletableFuture<Created> start(Player owner, OrderBuilder draft, Repetition repetition) {
        try {
            requirePermission(owner);
        } catch (CommandFailure failure) {
            return CompletableFuture.failedFuture(failure);
        }
        return start(owner.getUniqueId(), draft, repetition, limits.applyAsInt(owner));
    }

    /**
     * Starts a series for an owner who may be offline, with the repeat permission and the order limit known from
     * elsewhere, such as the permissions remembered with a dashboard session. Its first copy is created at once.
     *
     * @param owner      id of the player owning the draft
     * @param draft      the finished draft
     * @param repetition how the order repeats
     * @param mayRepeat  whether the owner holds the permission to create repeating orders
     * @param limit      how many active orders the owner may have
     * @return the first copy, or a {@link CommandFailure} when the series could not start
     */
    public CompletableFuture<Created> start(
            UUID owner, OrderBuilder draft, Repetition repetition, boolean mayRepeat, int limit) {
        try {
            check(repetition, mayRepeat);
        } catch (CommandFailure failure) {
            return CompletableFuture.failedFuture(failure);
        }
        return start(owner, draft, repetition, limit);
    }

    /**
     * Checks whether a series could start with a repetition, without starting it.
     *
     * @param repetition how the order would repeat
     * @param mayRepeat  whether the owner holds the permission to create repeating orders
     * @throws CommandFailure when the owner may not repeat orders or the repetition is not allowed
     */
    public void check(Repetition repetition, boolean mayRepeat) {
        if (!mayRepeat) throw CommandFailure.of("error.permission.repeatOrder");
        repetition.validate(settings.get(), Times.local(now()).toLocalDate());
    }

    private CompletableFuture<Created> start(UUID owner, OrderBuilder draft, Repetition repetition, int limit) {
        var current = settings.get();
        var now = now();
        try {
            repetition.validate(current, Times.local(now).toLocalDate());
        } catch (CommandFailure failure) {
            return CompletableFuture.failedFuture(failure);
        }
        var id = owner;
        var lines = draft.lines().stream().map(SeriesLine::of).toList();
        return data.retrieveActiveCount(id)
                .thenCombine(activeOrders.apply(id), (series, orders) -> {
                    requireRoom(series, current);
                    if (orders >= limit) throw CommandFailure.of("order.create.error.limitReached");
                    return newSeries(id, draft.name(), lines, repetition, limit, now);
                })
                .thenCompose(data::submitSeries)
                .thenCompose(saved -> saved.map(series -> first(series, now))
                        .orElseGet(() -> CompletableFuture.failedFuture(CommandFailure.of("error.saveFailed"))));
    }

    /**
     * Stops a series of a player. The current copy stays.
     *
     * @param owner  player who stops it
     * @param series id of the series
     * @return the series as it was before, or a {@link CommandFailure} when it is unknown, not the player's or not
     *         active
     */
    public CompletableFuture<OrderSeries> stop(UUID owner, int series) {
        return owned(owner, series)
                .thenCompose(found -> data.submitStop(series, SeriesState.STOPPED, SeriesStopReason.OWNER, now())
                        .thenApply(stopped -> {
                            if (!stopped) throw CommandFailure.of("order.series.error.notActive");
                            return found;
                        }));
    }

    /**
     * Resumes a stopped series of a player. Its next copy is due at once.
     *
     * @param owner  player who resumes it, online
     * @param series id of the series
     * @return the series as it was before, or a {@link CommandFailure} when it is unknown, not the player's, not
     *         stopped, past its end, or the player has too many active series
     */
    public CompletableFuture<OrderSeries> resume(Player owner, int series) {
        var current = settings.get();
        int limit;
        try {
            requirePermission(owner);
            if (!current.enabled()) throw CommandFailure.of("order.draft.repeat.error.disabled");
            limit = limits.applyAsInt(owner);
        } catch (CommandFailure failure) {
            return CompletableFuture.failedFuture(failure);
        }
        var id = owner.getUniqueId();
        var now = now();
        return owned(id, series)
                .thenCombine(data.retrieveActiveCount(id), (found, active) -> {
                    if (found.state() != SeriesState.STOPPED) throw CommandFailure.of("order.series.error.notStopped");
                    if (found.endReached(now)) throw CommandFailure.of("order.series.error.ended");
                    requireRoom(active, current);
                    return found;
                })
                .thenCompose(found -> data.submitOrderLimit(id, limit)
                        .thenCompose(ignored -> data.submitResume(series, now, now))
                        .thenApply(resumed -> {
                            if (!resumed) throw CommandFailure.of("order.series.error.notStopped");
                            return found;
                        }));
    }

    /**
     * Stops the series of a copy its owner cancelled.
     *
     * @param order the cancelled order
     * @return true when this stopped an active series
     */
    public CompletableFuture<Boolean> onCopyCancelled(SimpleOrder order) {
        var series = order.seriesId();
        if (series.isEmpty()) return CompletableFuture.completedFuture(false);
        return data.submitStop(series.getAsInt(), SeriesState.STOPPED, SeriesStopReason.COPY_CANCELLED, now());
    }

    /**
     * Stops the series of a copy an admin removed and tells the owner.
     *
     * @param order the removed order
     * @return true when this stopped an active series
     */
    public CompletableFuture<Boolean> onCopyRemovedByAdmin(SimpleOrder order) {
        var series = order.seriesId();
        if (series.isEmpty()) return CompletableFuture.completedFuture(false);
        var id = series.getAsInt();
        return data.submitStop(id, SeriesState.STOPPED, SeriesStopReason.ADMIN, now())
                .thenCompose(stopped -> {
                    if (!stopped) return CompletableFuture.completedFuture(false);
                    return data.retrieveSeries(id).thenApply(found -> {
                        found.ifPresent(notices::stopped);
                        return true;
                    });
                });
    }

    /**
     * Stores the order limit of a joining player on their series, because permissions are only known while the player
     * is online.
     *
     * @param event the join
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        data.submitOrderLimit(player.getUniqueId(), limits.applyAsInt(player)).exceptionally(err -> {
            logger.log(Level.SEVERE, "Could not store the order limit of " + player.getName(), err);
            return 0;
        });
    }

    /**
     * The orders of a series, as listeners of the order creation see one copy.
     *
     * @param series the series
     * @return a new draft
     */
    public static IOrderDraft draftOf(OrderSeries series) {
        var lines = series.lines().stream().map(SeriesLine::toDraftLine).toList();
        return OrderDraftService.toDraft(series.name(), lines);
    }

    /**
     * Whether the copy that was just claimed is the last one: the count limit is reached, or the following copy would
     * be due after the end date.
     *
     * @param series  series as it was before the claim
     * @param nextDue when the following copy is due
     * @return true when no further copy will be posted
     */
    static boolean lastCopy(OrderSeries series, LocalDateTime nextDue) {
        var count = series.maxCopies();
        if (count.isPresent() && series.posted() + 1 >= count.getAsInt()) return true;
        return series.ends().map(nextDue::isAfter).orElse(false);
    }

    private CompletableFuture<Decision> waitForLimit(OrderSeries series, LocalDateTime now) {
        return data.submitWaiting(series.id(), SeriesWait.ORDER_LIMIT, now).thenApply(first -> {
            if (first) notices.waitingLimit(series);
            return Decision.WAITING_LIMIT;
        });
    }

    private CompletableFuture<Created> first(OrderSeries series, LocalDateTime now) {
        return claimAndPost(series, series.nextDue(), now, false).thenCompose(posting -> {
            if (posting.created().isPresent())
                return CompletableFuture.completedFuture(posting.created().get());
            var reason =
                    posting.decision() == Decision.CANNOT_PAY ? SeriesStopReason.CANNOT_PAY : SeriesStopReason.INVALID;
            var failure = posting.failure().orElseGet(() -> CommandFailure.of("error.saveFailed"));
            var release = posting.decision() == Decision.SKIPPED
                    ? data.submitClaimRelease(series, now)
                    : CompletableFuture.completedFuture(true);
            return release.thenCompose(ignored -> data.submitStop(series.id(), SeriesState.STOPPED, reason, now))
                    .<Created>thenApply(ignored -> {
                        throw failure;
                    });
        });
    }

    private CompletableFuture<Posting> claimAndPost(
            OrderSeries series, LocalDateTime nextDue, LocalDateTime now, boolean announce) {
        return data.submitClaim(series, nextDue, now).thenCompose(claimed -> {
            if (!claimed) return CompletableFuture.completedFuture(Posting.of(Decision.LOST_CLAIM));
            return copies.post(series.owner(), draftOf(series))
                    .handle((outcome, err) ->
                            err == null ? settle(series, nextDue, now, announce, outcome) : failed(series, now, err))
                    .thenCompose(Function.identity());
        });
    }

    private CompletableFuture<Posting> settle(
            OrderSeries series, LocalDateTime nextDue, LocalDateTime now, boolean announce, Outcome outcome) {
        return switch (outcome) {
            case Created created -> posted(series, nextDue, now, announce, created);
            case Rejected rejected
            when rejected.reason() == RejectReason.INSUFFICIENT_FUNDS -> cannotPay(series, now, announce, rejected);
            case Rejected rejected -> {
                logger.info("A copy of repeating order " + series.id() + " was skipped: " + rejected.reason());
                yield CompletableFuture.completedFuture(
                        new Posting(Decision.SKIPPED, Optional.empty(), Optional.of(rejected.failure())));
            }
        };
    }

    private CompletableFuture<Posting> posted(
            OrderSeries series, LocalDateTime nextDue, LocalDateTime now, boolean announce, Created created) {
        var posting = new Posting(Decision.POSTED, Optional.of(created), Optional.empty());
        return data.submitCurrentOrder(series.id(), created.order().id()).thenCompose(stored -> {
            if (!stored)
                logger.warning("Could not link order " + created.order().id() + " to its series");
            if (announce) notices.posted(series, created, series.posted() + 1);
            if (!lastCopy(series, nextDue)) return CompletableFuture.completedFuture(posting);
            return finish(series, now, announce).thenApply(ignored -> posting);
        });
    }

    private CompletableFuture<Posting> cannotPay(
            OrderSeries series, LocalDateTime now, boolean announce, Rejected rejected) {
        return data.submitClaimRelease(series, now)
                .thenCompose(
                        ignored -> data.submitStop(series.id(), SeriesState.STOPPED, SeriesStopReason.CANNOT_PAY, now))
                .thenApply(stopped -> {
                    if (stopped && announce) notices.stopped(series);
                    return new Posting(Decision.CANNOT_PAY, Optional.empty(), Optional.of(rejected.failure()));
                });
    }

    private CompletableFuture<Posting> failed(OrderSeries series, LocalDateTime now, Throwable err) {
        var cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
        logger.log(Level.SEVERE, "Could not post a copy of repeating order " + series.id(), cause);
        var failure =
                cause instanceof CommandFailure commandFailure ? commandFailure : CommandFailure.of("error.saveFailed");
        return data.submitClaimRelease(series, now)
                .thenApply(ignored -> new Posting(Decision.FAILED, Optional.empty(), Optional.of(failure)));
    }

    private CompletableFuture<Boolean> finish(OrderSeries series, LocalDateTime now, boolean announce) {
        return data.submitStop(series.id(), SeriesState.FINISHED, null, now).thenApply(finished -> {
            if (finished && announce) notices.finished(series);
            return finished;
        });
    }

    private CompletableFuture<OrderSeries> owned(UUID owner, int series) {
        return data.retrieveSeries(series)
                .thenApply(found -> found.filter(candidate -> candidate.owner().equals(owner))
                        .orElseThrow(() -> CommandFailure.of("order.series.error.unknown")));
    }

    private static void requirePermission(Player owner) {
        if (!owner.hasPermission(Permission.Orders.REPEAT)) {
            throw CommandFailure.of("error.permission.repeatOrder");
        }
    }

    private static void requireRoom(int activeSeries, RepeatingSettings settings) {
        if (activeSeries >= settings.maxSeriesPerPlayer()) {
            throw CommandFailure.of(
                    "order.draft.repeat.error.maxSeries",
                    Replacement.create("max", String.valueOf(settings.maxSeriesPerPlayer())));
        }
    }

    private static OrderSeries newSeries(
            UUID owner, String name, List<SeriesLine> lines, Repetition repetition, int limit, LocalDateTime now) {
        return new OrderSeries(
                -1,
                owner,
                name,
                lines,
                repetition.interval(),
                repetition.count(),
                repetition.ends(),
                0,
                repetition.interval().next(now),
                OptionalInt.empty(),
                SeriesState.ACTIVE,
                Optional.empty(),
                Optional.empty(),
                limit,
                now);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * What was decided for a due series.
     */
    public enum Decision {
        /**
         * The series reached its end and finished.
         */
        FINISHED,
        /**
         * The previous copy still waits for a company.
         */
        WAITING_PREVIOUS,
        /**
         * The owner reached the order limit.
         */
        WAITING_LIMIT,
        /**
         * Another caller claimed the copy first.
         */
        LOST_CLAIM,
        /**
         * A copy was posted.
         */
        POSTED,
        /**
         * The copy was cancelled by an integration or refused; its interval counts.
         */
        SKIPPED,
        /**
         * The owner could not pay the copy; the series stopped.
         */
        CANNOT_PAY,
        /**
         * The copy could not be saved; the claim was released.
         */
        FAILED
    }

    /**
     * Creates the copies of repeating orders.
     */
    @FunctionalInterface
    public interface Copies {
        /**
         * Creates one copy, charging its owner price and fee.
         *
         * @param owner owner of the series
         * @param draft the copy
         * @return what came of it
         */
        CompletableFuture<Outcome> post(UUID owner, IOrderDraft draft);
    }

    /**
     * Tells owners about their repeating orders.
     */
    public interface Notices {
        /**
         * @param series the series as it was before the copy
         * @param copy   the posted copy
         * @param number number of the copy, starting at 1
         */
        void posted(OrderSeries series, Created copy, int number);

        /**
         * @param series series held back by the order limit
         */
        void waitingLimit(OrderSeries series);

        /**
         * @param series the stopped series
         */
        void stopped(OrderSeries series);

        /**
         * @param series the finished series
         */
        void finished(OrderSeries series);
    }

    /**
     * Notices through the inbox.
     *
     * @param notifications sends the notifications
     * @param money         formats amounts of money
     */
    record InboxNotices(NotificationService notifications, DoubleFunction<String> money) implements Notices {
        @Override
        public void posted(OrderSeries series, Created copy, int number) {
            var count = series.maxCopies();
            var copyText = count.isPresent() ? number + "/" + count.getAsInt() : String.valueOf(number);
            notifications.send(
                    series.owner(),
                    NotificationType.SERIES_POSTED,
                    named(series)
                            .money("amount", money.apply(copy.price() + copy.fee()))
                            .text("copy", copyText)
                            .number("order_id", copy.order().id()));
        }

        @Override
        public void waitingLimit(OrderSeries series) {
            notifications.send(
                    series.owner(),
                    NotificationType.SERIES_WAITING_LIMIT,
                    named(series).number("series_id", series.id()));
        }

        @Override
        public void stopped(OrderSeries series) {
            notifications.send(
                    series.owner(),
                    NotificationType.SERIES_STOPPED,
                    named(series).number("series_id", series.id()));
        }

        @Override
        public void finished(OrderSeries series) {
            notifications.send(series.owner(), NotificationType.SERIES_FINISHED, named(series));
        }

        private static NotificationArgs named(OrderSeries series) {
            return NotificationArgs.create().text("series_name", series.name());
        }
    }

    /**
     * What came of posting a copy.
     *
     * @param decision what was decided
     * @param created  the copy, when it was created
     * @param failure  why it was not created, when it was refused or failed
     */
    private record Posting(Decision decision, Optional<Created> created, Optional<CommandFailure> failure) {
        private static Posting of(Decision decision) {
            return new Posting(decision, Optional.empty(), Optional.empty());
        }
    }
}
