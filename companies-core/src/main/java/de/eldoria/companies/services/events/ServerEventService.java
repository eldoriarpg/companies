/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.components.event.EventState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.nodes.NodeMessage.ServerEventChanged;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Moves the server events through their life and changes them for admins.
 * <p>
 * Only the primary node runs the job: it starts a scheduled event at its start and ends a running one at its end. It
 * wakes at the next start or end of an event it knows, at the latest every {@value #CHECK_SECONDS} seconds, and right
 * after an event was changed on any node, which every node hears through the {@link ServerEventChanged} signal. An
 * event whose whole time passed while the primary was down ends without starting. {@link #stop()} ends the job before
 * its executor shuts down.
 * <p>
 * Every node runs the changes of admins: {@link #create}, {@link #end}, {@link #cancel}, {@link #extend} and
 * {@link #budget}, and {@link #deplete} for a payout its funding cannot cover. Every move is conditional on what was
 * read, see {@link AServerEventData}, so the job and a command, or two nodes, never move an event twice; only the
 * winner signals the change. Times are kept to the second, as every dialect stores them. Methods block; call them off
 * the main thread.
 */
public final class ServerEventService {
    /**
     * Seconds between two checks of the job at most.
     */
    public static final int CHECK_SECONDS = 60;

    /**
     * How far ahead of now an event may end at most, when it is created or extended.
     */
    public static final Duration MAX_AHEAD = Duration.ofDays(365);

    private static final long MIN_DELAY_MILLIS = 250L;
    private static final int ATTEMPTS = 3;

    private final AServerEventData events;
    private final Function<String, @Nullable String> canonical;
    private final Consumer<ServerEventChanged> signal;
    private final Clock clock;
    private final Logger logger;
    private final Schedule schedule;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final AtomicReference<@Nullable ScheduledFuture<?>> next = new AtomicReference<>();
    private volatile @Nullable ScheduledExecutorService executor;
    private volatile boolean stopped;

    /**
     * @param events    event repository
     * @param canonical the name of the item a material name means, as order lines store it, such as
     *                  {@code DIAMOND_BLOCK} for {@code minecraft:diamond_block}; null when it names no item
     * @param signal    tells every node that an event changed, see {@link de.eldoria.companies.nodes.EventNetwork}
     * @param clock     clock giving the current time in UTC
     * @param logger    reports failed checks
     */
    public ServerEventService(
            AServerEventData events,
            Function<String, @Nullable String> canonical,
            Consumer<ServerEventChanged> signal,
            Clock clock,
            Logger logger) {
        this(events, canonical, signal, clock, logger, now -> List.of());
    }

    /**
     * @param events    event repository
     * @param canonical the name of the item a material name means, as order lines store it, such as
     *                  {@code DIAMOND_BLOCK} for {@code minecraft:diamond_block}; null when it names no item
     * @param signal    tells every node that an event changed, see {@link de.eldoria.companies.nodes.EventNetwork}
     * @param clock     clock giving the current time in UTC
     * @param logger    reports failed checks
     * @param schedule  the events of the templates of {@code events.yml} that are due, created by the job
     */
    public ServerEventService(
            AServerEventData events,
            Function<String, @Nullable String> canonical,
            Consumer<ServerEventChanged> signal,
            Clock clock,
            Logger logger,
            Schedule schedule) {
        this.schedule = schedule;
        this.events = events;
        this.canonical = canonical;
        this.signal = signal;
        this.clock = clock;
        this.logger = logger;
    }

    /**
     * Runs the job on this node, the primary: a check now, then at every start and end, until {@link #stop()}.
     *
     * @param executor executor running the checks
     */
    public synchronized void schedule(ScheduledExecutorService executor) {
        this.executor = executor;
        stopped = false;
        pending.set(false);
        checkSoon();
    }

    /**
     * Stops the job: the planned check is cancelled and none is planned anymore, so the executor can shut down at
     * once. Call it before the executor is shut down. It never waits for a running check, which plans nothing once
     * it sees the job stopped.
     */
    public void stop() {
        stopped = true;
        executor = null;
        cancel(next.getAndSet(null));
    }

    /**
     * Runs a check soon, after an event changed. Does nothing on a node that does not run the job, and once the job
     * stopped. Calls while a check waits to run share that check, so a burst of changes queues one check only.
     */
    public void checkSoon() {
        var running = executor;
        if (stopped || running == null || running.isShutdown()) return;
        if (!pending.compareAndSet(false, true)) return;
        try {
            running.execute(this::check);
        } catch (RejectedExecutionException e) {
            pending.set(false);
            logger.log(Level.FINE, "Not checking the server events while the plugin stops.", e);
        }
    }

    /**
     * Runs a check and plans the next one under one lock, so the plan of an older check never replaces a newer one.
     * A change signalled from here on asks for another check.
     */
    private synchronized void check() {
        pending.set(false);
        if (!running()) return;
        @Nullable LocalDateTime due = null;
        try {
            due = run().next();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not look after the server events", e);
        }
        plan(due);
    }

    private boolean running() {
        var current = executor;
        return !stopped && current != null && !current.isShutdown();
    }

    private void plan(@Nullable LocalDateTime due) {
        var running = executor;
        if (!running() || running == null) return;
        var delay = TimeUnit.SECONDS.toMillis(CHECK_SECONDS);
        if (due != null) {
            delay = Math.clamp(Duration.between(now(), due).toMillis(), MIN_DELAY_MILLIS, delay);
        }
        ScheduledFuture<?> planned;
        try {
            planned = running.schedule(this::check, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            logger.log(Level.FINE, "Not planning the next check of the server events while the plugin stops.", e);
            return;
        }
        cancel(next.getAndSet(planned));
        if (stopped) cancel(next.getAndSet(null));
    }

    private static void cancel(@Nullable ScheduledFuture<?> future) {
        if (future != null) future.cancel(false);
    }

    /**
     * Starts the scheduled events whose start passed and ends the events whose end passed. An end is conditional on
     * the end that was read, so an event extended meanwhile keeps running. A scheduled event whose whole time passed
     * ends without starting and is signalled as {@link ServerEventChanged#MISSED}.
     *
     * @return what the check did
     */
    public synchronized Run run() {
        var now = now();
        createDue(now);
        var started = new ArrayList<Integer>();
        var ended = new ArrayList<Integer>();
        var missed = new ArrayList<Integer>();
        var withdrawn = new ArrayList<Integer>();
        @Nullable LocalDateTime due = null;
        for (var event : events.live()) {
            var id = event.id();
            var scheduled = event.state() == EventState.SCHEDULED;
            if (scheduled && !keeps(event)) {
                if (move(id, EventState.SCHEDULED, EventState.CANCELLED, ServerEvent.TEMPLATE_BY, now)) {
                    withdrawn.add(id);
                }
                continue;
            }
            if (!event.ends().isAfter(now)) {
                if (!events.expire(id, event.state(), event.ends(), scheduled ? ServerEvent.MISSED_BY : null, now)) {
                    continue;
                }
                if (scheduled) {
                    missed.add(id);
                    signal(id, ServerEventChanged.MISSED);
                } else {
                    ended.add(id);
                    signal(id, EventState.ENDED.name());
                }
            } else if (scheduled && !event.starts().isAfter(now)) {
                if (!move(id, EventState.SCHEDULED, EventState.ACTIVE, null, now)) continue;
                started.add(id);
                due = earlier(due, event.ends());
            } else {
                due = earlier(due, scheduled ? event.starts() : event.ends());
            }
        }
        return new Run(started, ended, missed, withdrawn, due);
    }

    private static @Nullable LocalDateTime earlier(@Nullable LocalDateTime current, LocalDateTime candidate) {
        return current == null || candidate.isBefore(current) ? candidate : current;
    }

    /**
     * Stores a new event that waits for its start. The job starts it at once when its start already passed.
     * <p>
     * Each material must name an item and is stored by the name of that item, so {@code minecraft:diamond_block} and
     * {@code diamond block} both become {@code DIAMOND_BLOCK}, the name order lines store; duplicates are dropped after
     * that, and the levels lose their duplicates too. The end must lie after now and at most {@link #MAX_AHEAD} ahead.
     *
     * @param draft the event, see {@link ServerEvent#draft}
     * @return the stored event's id, or why it was not stored
     */
    public Created create(ServerEvent draft) {
        var materials = new LinkedHashSet<String>();
        var unknown = false;
        for (var material : draft.materials()) {
            var name = canonical.apply(material.strip());
            if (name == null) unknown = true;
            else materials.add(name);
        }
        var event = new ServerEvent(
                0,
                draft.name().strip(),
                draft.kind(),
                draft.value(),
                List.copyOf(materials),
                draft.levels().stream().distinct().toList(),
                second(draft.starts()),
                second(draft.ends()),
                draft.funding(),
                draft.account(),
                draft.budget(),
                0,
                EventState.SCHEDULED,
                draft.template(),
                draft.createdBy(),
                second(draft.created()),
                null,
                null);
        var problem = event.problem();
        if (problem.isEmpty() && unknown) problem = Optional.of(ServerEvent.Problem.MATERIALS);
        if (problem.isEmpty() && !fits(event.ends())) problem = Optional.of(ServerEvent.Problem.TIMES);
        if (problem.isPresent()) return new Created(0, problem.get());
        var id = events.create(event);
        if (id.isEmpty()) return new Created(0, ServerEvent.Problem.STORAGE);
        signal(id.get(), EventState.SCHEDULED.name());
        return new Created(id.get(), null);
    }

    private boolean fits(LocalDateTime ends) {
        var now = now();
        return ends.isAfter(now) && !ends.isAfter(now.plus(MAX_AHEAD));
    }

    /**
     * @param createdBy who created an event
     * @return the first event created by them, empty when there is none
     */
    public Optional<ServerEvent> createdBy(String createdBy) {
        return events.createdBy(createdBy);
    }

    /**
     * @param id id of an event
     * @return the event as it is stored now, empty when there is none
     */
    public Optional<ServerEvent> event(int id) {
        return events.event(id);
    }

    /**
     * Ends a running event early. A scheduled one is {@link #cancel cancelled} instead.
     *
     * @param id id of the event
     * @param by who ends it
     * @return what happened
     */
    public Change end(int id, String by) {
        for (var attempt = 0; attempt < ATTEMPTS; attempt++) {
            var event = events.event(id);
            if (event.isEmpty()) return Change.UNKNOWN;
            var state = event.get().state();
            if (state != EventState.ACTIVE) return state.closed() ? Change.CLOSED : Change.REFUSED;
            if (move(id, state, EventState.ENDED, by, now())) return Change.DONE;
        }
        return Change.FAILED;
    }

    /**
     * Cancels an event that waits or runs. A scheduled one never starts and is signalled as
     * {@link ServerEventChanged#WITHDRAWN}, so it is not announced; nothing is paid back.
     *
     * @param id id of the event
     * @param by who cancels it
     * @return what happened
     */
    public Change cancel(int id, String by) {
        for (var attempt = 0; attempt < ATTEMPTS; attempt++) {
            var event = events.event(id);
            if (event.isEmpty()) return Change.UNKNOWN;
            var state = event.get().state();
            if (state.closed()) return Change.CLOSED;
            if (move(id, state, EventState.CANCELLED, by, now())) return Change.DONE;
        }
        return Change.FAILED;
    }

    /**
     * Moves the end of an event that waits or runs later. The new end must lie after now and at most
     * {@link #MAX_AHEAD} ahead.
     *
     * @param id       id of the event
     * @param duration how much later it ends, at least a second
     * @return what happened
     */
    public Change extend(int id, Duration duration) {
        if (duration.compareTo(Duration.ofSeconds(1)) < 0) return Change.REFUSED;
        for (var attempt = 0; attempt < ATTEMPTS; attempt++) {
            var event = events.event(id);
            if (event.isEmpty()) return Change.UNKNOWN;
            var current = event.get();
            if (current.state().closed()) return Change.CLOSED;
            LocalDateTime ends;
            try {
                ends = second(current.ends().plus(duration));
            } catch (DateTimeException | ArithmeticException e) {
                return Change.REFUSED;
            }
            if (!fits(ends)) return Change.REFUSED;
            if (events.ends(id, current.ends(), ends)) {
                signal(id, ServerEventChanged.CHANGED);
                return Change.DONE;
            }
        }
        return Change.FAILED;
    }

    /**
     * Sets the budget of a reward multiplier that waits or runs. It is never set below what the event paid already;
     * only one funded from an account may go without.
     *
     * @param id     id of the event
     * @param budget the new budget, above 0; null for none
     * @return what happened
     */
    public Change budget(int id, @Nullable Double budget) {
        var event = events.event(id);
        if (event.isEmpty()) return Change.UNKNOWN;
        var current = event.get();
        if (current.state().closed()) return Change.CLOSED;
        if (!current.kind().funded()) return Change.REFUSED;
        if (budget == null && current.funding() != MoneySource.SourceType.ACCOUNT) return Change.REFUSED;
        if (budget != null && (!Double.isFinite(budget) || !(budget > 0) || budget < current.spent())) {
            return Change.REFUSED;
        }
        if (!events.budget(id, budget)) {
            var now = events.event(id);
            if (now.isEmpty() || now.get().state().closed()) return Change.CLOSED;
            return budget != null && budget < now.get().spent() ? Change.REFUSED : Change.FAILED;
        }
        signal(id, ServerEventChanged.CHANGED);
        return Change.DONE;
    }

    /**
     * Ends a running multiplier whose funding cannot cover its part of a payout anymore.
     *
     * @param id id of the event
     * @return true when it ran and is depleted now
     */
    public boolean deplete(int id) {
        return move(id, EventState.ACTIVE, EventState.DEPLETED, null, now());
    }

    private boolean keeps(ServerEvent event) {
        try {
            return schedule.keeps(event);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not check the template of server event " + event.id(), e);
            return true;
        }
    }

    private void createDue(LocalDateTime now) {
        List<ServerEvent> due;
        try {
            due = schedule.due(now);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not read the templates of events.yml", e);
            return;
        }
        for (var draft : due) {
            var created = create(draft);
            if (!created.stored()) schedule.refused(draft, Objects.requireNonNull(created.problem()));
        }
    }

    private boolean move(int id, EventState from, EventState to, @Nullable String by, LocalDateTime at) {
        if (!events.state(id, from, to, by, at)) return false;
        var change =
                to == EventState.CANCELLED && from == EventState.SCHEDULED ? ServerEventChanged.WITHDRAWN : to.name();
        signal(id, change);
        return true;
    }

    private void signal(int id, String change) {
        try {
            signal.accept(new ServerEventChanged(id, change));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not signal the change of server event " + id + ".", e);
        }
    }

    private LocalDateTime now() {
        return second(LocalDateTime.now(clock));
    }

    private static LocalDateTime second(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * The events the templates of {@code events.yml} want created, see {@link EventSchedule}.
     */
    @FunctionalInterface
    public interface Schedule {
        /**
         * @param now the current time in UTC
         * @return the events that are due to be created now
         */
        List<ServerEvent> due(LocalDateTime now);

        /**
         * Hears that a due event could not be stored.
         *
         * @param draft   the event
         * @param problem why it was not stored
         */
        default void refused(ServerEvent draft, ServerEvent.Problem problem) {}

        /**
         * Whether an event that waits for its start is kept; one that is not is withdrawn, noting
         * {@link ServerEvent#TEMPLATE_BY} as who closed it.
         *
         * @param event an event waiting for its start
         * @return false when its template no longer wants it
         */
        default boolean keeps(ServerEvent event) {
            return true;
        }
    }

    /**
     * What a check of the job did.
     *
     * @param started the events it started
     * @param ended   the running events it ended at their end
     * @param missed    the scheduled events it ended without starting, since their whole time passed
     * @param withdrawn the scheduled events it cancelled, since their template was disabled or removed
     * @param next      the next start or end of an event it knows, null when none waits or runs
     */
    public record Run(
            List<Integer> started,
            List<Integer> ended,
            List<Integer> missed,
            List<Integer> withdrawn,
            @Nullable LocalDateTime next) {
        /**
         * @param started   the events it started
         * @param ended     the running events it ended
         * @param missed    the scheduled events it ended without starting
         * @param withdrawn the scheduled events it cancelled for their template
         * @param next      the next start or end
         */
        public Run {
            started = List.copyOf(started);
            ended = List.copyOf(ended);
            missed = List.copyOf(missed);
            withdrawn = List.copyOf(withdrawn);
        }
    }

    /**
     * The outcome of {@link #create}.
     *
     * @param id      id of the stored event, 0 when it was not stored
     * @param problem why it was not stored, null when it was
     */
    public record Created(int id, ServerEvent.@Nullable Problem problem) {
        /**
         * @return whether the event was stored
         */
        public boolean stored() {
            return problem == null;
        }
    }

    /**
     * The outcome of a change of an event.
     */
    public enum Change {
        /**
         * The event changed.
         */
        DONE,
        /**
         * There is no such event.
         */
        UNKNOWN,
        /**
         * The event is closed already, or closed while it was changed.
         */
        CLOSED,
        /**
         * The change does not fit the event: ending one that has not started, a budget for another kind, below what
         * it spent or missing for a minted one, or an extension shorter than a second or to an end that is not after
         * now or more than {@link #MAX_AHEAD} ahead.
         */
        REFUSED,
        /**
         * The database did not take the change, or the event kept changing meanwhile; nothing changed.
         */
        FAILED
    }
}
