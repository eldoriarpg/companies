/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.dashboard.MutableClock;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteServerEventData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.data.wrapper.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.nodes.NodeMessage.ServerEventChanged;
import de.eldoria.companies.services.events.ServerEventService.Change;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerEventServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);
    private static final Set<String> KNOWN = Set.of("WHEAT", "CARROT", "DIAMOND_BLOCK");

    @TempDir
    Path directory;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-30T12:00:00Z"));
    private final List<ServerEventChanged> signals = new CopyOnWriteArrayList<>();
    private SqLiteServerEventData events;
    private ServerEventService service;
    private Logger logger;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        events = new SqLiteServerEventData();
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        this.logger = logger;
        service = new ServerEventService(events, ServerEventServiceTest::canonical, signals::add, clock, logger);
    }

    @Test
    void anEventStartsAtItsStartAndEndsAtItsEnd() {
        var id = create(tax(NOW.plusHours(1), NOW.plusHours(3)));
        assertEquals(List.of(new ServerEventChanged(id, "SCHEDULED")), signals);

        var before = service.run();
        assertTrue(before.started().isEmpty());
        assertEquals(NOW.plusHours(1), before.next());

        clock.advance(Duration.ofHours(1));
        var start = service.run();
        assertEquals(List.of(id), start.started());
        assertEquals(NOW.plusHours(3), start.next());
        assertEquals(EventState.ACTIVE, state(id));

        clock.advance(Duration.ofHours(2));
        var end = service.run();
        assertEquals(List.of(id), end.ended());
        assertNull(end.next());
        var ended = events.event(id).orElseThrow();
        assertEquals(EventState.ENDED, ended.state());
        assertEquals(NOW.plusHours(3), ended.closed());
        assertNull(ended.closedBy());
        assertEquals(
                List.of("SCHEDULED", "ACTIVE", "ENDED"),
                signals.stream().map(ServerEventChanged::change).toList());
    }

    @Test
    void anEventWhoseWholeTimePassedEndsWithoutStarting() {
        var id = create(tax(NOW.plusMinutes(10), NOW.plusMinutes(20)));
        clock.advance(Duration.ofHours(1));

        var run = service.run();

        assertEquals(List.of(id), run.missed());
        assertTrue(run.started().isEmpty());
        assertEquals(EventState.ENDED, state(id));
        assertEquals(ServerEvent.MISSED_BY, events.event(id).orElseThrow().closedBy());
        assertEquals(new ServerEventChanged(id, ServerEventChanged.MISSED), signals.getLast());
        assertTrue(signals.stream().noneMatch(signal -> signal.change().equals("ENDED")));
    }

    @Test
    void anExtensionWhileTheJobEndsTheEventKeepsItRunning() {
        var id = create(tax(NOW, NOW.plusHours(1)));
        service.run();
        var racing = new SqLiteServerEventData() {
            @Override
            public List<ServerEvent> live() {
                var read = super.live();
                service.extend(id, Duration.ofHours(1));
                return read;
            }
        };
        var job = new ServerEventService(racing, ServerEventServiceTest::canonical, signals::add, clock, logger);
        clock.advance(Duration.ofMinutes(90));

        var run = job.run();

        assertTrue(run.ended().isEmpty());
        var event = events.event(id).orElseThrow();
        assertEquals(EventState.ACTIVE, event.state());
        assertEquals(NOW.plusHours(2), event.ends());
    }

    @Test
    void theMaterialsAreNormalisedAndMustNameItems() {
        var id = create(ServerEvent.draft(
                "Harvest",
                EventKind.TAX_REDUCTION,
                5,
                List.of(" wheat", "WHEAT", "minecraft:diamond_block", "diamond block", "carrot"),
                List.of(2, 2, 3),
                NOW,
                NOW.plusHours(1),
                null,
                null,
                null,
                null,
                "Nora",
                NOW));
        var unknown = service.create(ServerEvent.draft(
                "Stone",
                EventKind.TAX_REDUCTION,
                5,
                List.of("STONE"),
                List.of(),
                NOW,
                NOW.plusHours(1),
                null,
                null,
                null,
                null,
                "Nora",
                NOW));
        var comma = service.create(ServerEvent.draft(
                "Comma",
                EventKind.TAX_REDUCTION,
                5,
                List.of("WHEAT,CARROT"),
                List.of(),
                NOW,
                NOW.plusHours(1),
                null,
                null,
                null,
                null,
                "Nora",
                NOW));

        var event = events.event(id).orElseThrow();
        assertEquals(List.of("WHEAT", "DIAMOND_BLOCK", "CARROT"), event.materials());
        assertEquals(List.of(2, 3), event.levels());
        assertEquals(ServerEvent.Problem.MATERIALS, unknown.problem());
        assertEquals(ServerEvent.Problem.MATERIALS, comma.problem());
    }

    @Test
    void anEndMayLieAtMostAYearAhead() {
        var far = service.create(tax(NOW, NOW.plus(ServerEventService.MAX_AHEAD).plusSeconds(1)));
        var id = create(tax(NOW, NOW.plus(ServerEventService.MAX_AHEAD)));

        assertEquals(ServerEvent.Problem.TIMES, far.problem());
        assertEquals(Change.REFUSED, service.extend(id, Duration.ofSeconds(1)));
        assertEquals(Change.REFUSED, service.extend(id, Duration.ofSeconds(Long.MAX_VALUE)));
        assertEquals(
                NOW.plus(ServerEventService.MAX_AHEAD),
                events.event(id).orElseThrow().ends());
    }

    @Test
    void anExtensionMustEndAfterNow() {
        var id = create(tax(NOW, NOW.plusHours(1)));
        service.run();
        clock.advance(Duration.ofHours(3));

        assertEquals(Change.REFUSED, service.extend(id, Duration.ofHours(1)));
        assertEquals(Change.DONE, service.extend(id, Duration.ofHours(3)));
    }

    @Test
    void anUpdateTheDatabaseDoesNotTakeIsAFailureAndNotAClose() {
        var id = create(tax(NOW, NOW.plusHours(1)));
        var refusing = new SqLiteServerEventData() {
            @Override
            public boolean state(int event, EventState from, EventState to, @Nullable String by, LocalDateTime at) {
                return false;
            }

            @Override
            public boolean ends(int event, LocalDateTime from, LocalDateTime to) {
                return false;
            }
        };
        var failing = new ServerEventService(refusing, ServerEventServiceTest::canonical, signals::add, clock, logger);

        assertEquals(Change.FAILED, failing.cancel(id, "Nora"));
        assertEquals(Change.FAILED, failing.extend(id, Duration.ofHours(1)));
        assertEquals(EventState.SCHEDULED, state(id));
    }

    @Test
    void aBurstOfChangesQueuesOneCheck() throws Exception {
        var executor = new ScheduledThreadPoolExecutor(1);
        executor.setRemoveOnCancelPolicy(true);
        var release = new CountDownLatch(1);
        var blocking = new CountDownLatch(1);
        try {
            create(tax(NOW.plusHours(1), NOW.plusHours(2)));
            executor.execute(() -> {
                blocking.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            blocking.await();
            service.schedule(executor);
            for (var call = 0; call < 5; call++) service.checkSoon();
            assertEquals(1, executor.getQueue().size());

            release.countDown();
            awaitCompleted(executor, 2);
            assertEquals(2, executor.getCompletedTaskCount());
            assertEquals(1, executor.getQueue().size());

            service.checkSoon();
            awaitCompleted(executor, 3);
            assertEquals(3, executor.getCompletedTaskCount());
            assertEquals(1, executor.getQueue().size());
        } finally {
            release.countDown();
            service.stop();
            executor.shutdownNow();
        }
    }

    @Test
    void theJobStopsWithoutAPlannedCheck() throws Exception {
        var executor = new ScheduledThreadPoolExecutor(1);
        executor.setRemoveOnCancelPolicy(true);
        try {
            create(tax(NOW.plusHours(1), NOW.plusHours(2)));
            service.schedule(executor);
            awaitCompleted(executor, 1);
            assertEquals(1, executor.getQueue().size());

            service.stop();
            assertEquals(0, executor.getQueue().size());
            service.checkSoon();
            Thread.sleep(100);
            assertEquals(0, executor.getQueue().size());
            assertEquals(1, executor.getCompletedTaskCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aShutDownExecutorPlansNoCheck() {
        var executor = new ScheduledThreadPoolExecutor(1);
        executor.shutdown();

        service.schedule(executor);
        service.checkSoon();

        assertEquals(0, executor.getQueue().size());
        assertTrue(executor.isShutdown());
    }

    private static @Nullable String canonical(String material) {
        var name = material.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "").replace(' ', '_');
        return KNOWN.contains(name) ? name : null;
    }

    private static void awaitCompleted(ScheduledThreadPoolExecutor executor, long tasks) throws InterruptedException {
        var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while ((executor.getCompletedTaskCount() < tasks || executor.getActiveCount() > 0)
                && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
    }

    @Test
    void aSecondCheckMovesNothingTwice() {
        var id = create(tax(NOW, NOW.plusHours(1)));

        assertEquals(List.of(id), service.run().started());
        assertTrue(service.run().started().isEmpty());
        assertEquals(
                1,
                signals.stream()
                        .filter(signal -> signal.change().equals("ACTIVE"))
                        .count());
    }

    @Test
    void anEventIsRefusedWhenItDoesNotFitOrEndedAlready() {
        var invalid = service.create(ServerEvent.draft(
                "Free",
                EventKind.REWARD_MULTIPLIER,
                2,
                List.of(),
                List.of(),
                NOW,
                NOW.plusHours(1),
                SourceType.MINTED,
                null,
                null,
                null,
                "Nora",
                NOW));
        var past = service.create(tax(NOW.minusHours(2), NOW.minusHours(1)));

        assertEquals(ServerEvent.Problem.BUDGET, invalid.problem());
        assertEquals(ServerEvent.Problem.TIMES, past.problem());
        assertTrue(events.live().isEmpty());
        assertTrue(signals.isEmpty());
    }

    @Test
    void anAdminEndsARunningEventAndCancelsAScheduledOne() {
        var running = create(tax(NOW, NOW.plusHours(4)));
        var waiting = create(tax(NOW.plusHours(1), NOW.plusHours(4)));
        service.run();

        assertEquals(Change.REFUSED, service.end(waiting, "Nora"));
        assertEquals(Change.DONE, service.end(running, "Nora"));
        assertEquals(Change.CLOSED, service.end(running, "Nora"));
        assertEquals(Change.DONE, service.cancel(waiting, "Nora"));
        assertEquals(Change.CLOSED, service.cancel(waiting, "Nora"));
        assertEquals(Change.UNKNOWN, service.cancel(waiting + 10, "Nora"));

        var ended = events.event(running).orElseThrow();
        assertEquals(EventState.ENDED, ended.state());
        assertEquals("Nora", ended.closedBy());
        assertEquals(EventState.CANCELLED, state(waiting));

        clock.advance(Duration.ofHours(2));
        var run = service.run();
        assertTrue(run.started().isEmpty());
        assertNull(run.next());
    }

    @Test
    void aCancelBeforeTheStartIsWithdrawnAndOneAfterItIsCancelled() {
        var waiting = create(tax(NOW.plusHours(1), NOW.plusHours(4)));
        var running = create(tax(NOW, NOW.plusHours(4)));
        service.run();

        service.cancel(waiting, "Nora");
        service.cancel(running, "Nora");

        assertTrue(signals.contains(new ServerEventChanged(waiting, ServerEventChanged.WITHDRAWN)));
        assertTrue(signals.contains(new ServerEventChanged(running, "CANCELLED")));
        assertEquals(EventState.CANCELLED, state(waiting));
    }

    @Test
    void theJobCreatesTheDueEventsOfTheTemplatesAndHearsWhatWasRefused() {
        List<ServerEvent.Problem> refused = new CopyOnWriteArrayList<>();
        var templated = ServerEvent.draft(
                "Weekend",
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                NOW,
                NOW.plusHours(2),
                null,
                null,
                null,
                "weekend",
                "events.yml",
                NOW);
        var broken = ServerEvent.draft(
                "Broken",
                EventKind.TAX_REDUCTION,
                0,
                List.of(),
                List.of(),
                NOW,
                NOW.plusHours(2),
                null,
                null,
                null,
                "broken",
                "events.yml",
                NOW);
        var job = new ServerEventService(
                events,
                ServerEventServiceTest::canonical,
                signals::add,
                clock,
                logger,
                new ServerEventService.Schedule() {
                    @Override
                    public List<ServerEvent> due(LocalDateTime now) {
                        return List.of(templated, broken);
                    }

                    @Override
                    public void refused(ServerEvent draft, ServerEvent.Problem problem) {
                        refused.add(problem);
                    }
                });

        var run = job.run();

        assertEquals(1, run.started().size());
        var created = events.event(run.started().getFirst()).orElseThrow();
        assertEquals("weekend", created.template());
        assertEquals(EventState.ACTIVE, created.state());
        assertEquals(List.of(ServerEvent.Problem.VALUE), refused);
    }

    @Test
    void aWaitingEventWhoseTemplateIsGoneIsWithdrawnAndARunningOneKeepsRunning() {
        var waiting = create(templated("weekend", NOW.plusHours(1)));
        var running = create(templated("weekend", NOW));
        service.run();
        var job = new ServerEventService(
                events,
                ServerEventServiceTest::canonical,
                signals::add,
                clock,
                logger,
                new ServerEventService.Schedule() {
                    @Override
                    public List<ServerEvent> due(LocalDateTime now) {
                        return List.of();
                    }

                    @Override
                    public boolean keeps(ServerEvent event) {
                        return false;
                    }
                });

        var run = job.run();

        assertEquals(List.of(waiting), run.withdrawn());
        var withdrawn = events.event(waiting).orElseThrow();
        assertEquals(EventState.CANCELLED, withdrawn.state());
        assertEquals(ServerEvent.TEMPLATE_BY, withdrawn.closedBy());
        assertTrue(signals.contains(new ServerEventChanged(waiting, ServerEventChanged.WITHDRAWN)));
        assertEquals(EventState.ACTIVE, state(running));
    }

    private static ServerEvent templated(String template, LocalDateTime starts) {
        return ServerEvent.draft(
                "Weekend",
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                starts,
                starts.plusHours(4),
                null,
                null,
                null,
                template,
                "events.yml",
                NOW);
    }

    @Test
    void theJobAndAnAdminNeverMoveAnEventTwice() {
        var id = create(tax(NOW, NOW.plusHours(1)));
        events.state(id, EventState.SCHEDULED, EventState.CANCELLED, "Nora", NOW);

        assertTrue(service.run().started().isEmpty());
        assertEquals(EventState.CANCELLED, state(id));
    }

    @Test
    void anExtensionMovesTheEndLaterWhileTheEventRuns() {
        var id = create(tax(NOW, NOW.plusHours(1)));
        service.run();

        assertEquals(Change.DONE, service.extend(id, Duration.ofMinutes(90)));
        assertEquals(NOW.plusMinutes(150), events.event(id).orElseThrow().ends());
        assertEquals(Change.REFUSED, service.extend(id, Duration.ZERO));

        clock.advance(Duration.ofHours(2));
        assertTrue(service.run().ended().isEmpty());
        clock.advance(Duration.ofMinutes(30));
        assertEquals(List.of(id), service.run().ended());
        assertEquals(Change.CLOSED, service.extend(id, Duration.ofHours(1)));
        assertTrue(signals.contains(new ServerEventChanged(id, ServerEventChanged.CHANGED)));
    }

    @Test
    void aBudgetFitsTheFundingAndWhatWasSpent() {
        var account = create(multiplier(SourceType.ACCOUNT, "Bank", 100.0));
        var minted = create(multiplier(SourceType.MINTED, null, 100.0));
        var tax = create(tax(NOW, NOW.plusHours(1)));
        query("UPDATE server_events SET spent = ? WHERE id = ?")
                .single(call().bind(60.0).bind(account))
                .update();

        assertEquals(Change.REFUSED, service.budget(account, 50.0));
        assertEquals(Change.DONE, service.budget(account, 200.0));
        assertEquals(Change.DONE, service.budget(account, null));
        assertEquals(Change.REFUSED, service.budget(minted, null));
        assertEquals(Change.REFUSED, service.budget(minted, -5.0));
        assertEquals(Change.DONE, service.budget(minted, 300.0));
        assertEquals(Change.REFUSED, service.budget(tax, 10.0));
        assertNull(events.event(account).orElseThrow().budget());
        assertEquals(300.0, events.event(minted).orElseThrow().budget());

        service.cancel(minted, "Nora");
        assertEquals(Change.CLOSED, service.budget(minted, 400.0));
    }

    @Test
    void aRunningMultiplierIsDepletedOnce() {
        var id = create(multiplier(SourceType.MINTED, null, 100.0));
        service.run();

        assertTrue(service.deplete(id));
        assertTrue(!service.deplete(id));
        assertEquals(EventState.DEPLETED, state(id));
        assertTrue(signals.contains(new ServerEventChanged(id, "DEPLETED")));
    }

    @Test
    void timesAreKeptToTheSecond() {
        var id = create(tax(NOW.plusNanos(700_000_000), NOW.plusHours(1).plusNanos(1)));

        var event = events.event(id).orElseThrow();
        assertEquals(NOW, event.starts());
        assertEquals(NOW.plusHours(1), event.ends());
    }

    @Test
    void theJobWakesAtTheStartOfAnEvent() throws Exception {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var live = new ServerEventService(
                events, ServerEventServiceTest::canonical, signals::add, Clock.systemUTC(), logger);
        var now = LocalDateTime.now(Clock.systemUTC());
        var id = live.create(tax(now.plusSeconds(2), now.plusHours(1))).id();
        var executor = Executors.newSingleThreadScheduledExecutor();
        try {
            live.schedule(executor);
            var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (state(id) != EventState.ACTIVE && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(EventState.ACTIVE, state(id));
        } finally {
            live.stop();
            executor.shutdownNow();
        }
    }

    private int create(ServerEvent draft) {
        var created = service.create(draft);
        assertTrue(created.stored(), String.valueOf(created.problem()));
        return created.id();
    }

    private @Nullable EventState state(int id) {
        return events.event(id).map(ServerEvent::state).orElse(null);
    }

    private static ServerEvent tax(LocalDateTime starts, LocalDateTime ends) {
        return ServerEvent.draft(
                " Tax holiday ",
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                starts,
                ends,
                null,
                null,
                null,
                null,
                "Nora",
                NOW);
    }

    private static ServerEvent multiplier(SourceType funding, @Nullable String account, double budget) {
        return ServerEvent.draft(
                "Double pay",
                EventKind.REWARD_MULTIPLIER,
                2,
                List.of(),
                List.of(),
                NOW,
                NOW.plusHours(4),
                funding,
                account,
                budget,
                null,
                "Nora",
                NOW);
    }
}
