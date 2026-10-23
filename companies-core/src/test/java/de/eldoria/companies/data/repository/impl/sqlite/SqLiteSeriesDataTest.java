/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.series.OrderSeries;
import de.eldoria.companies.data.wrapper.series.RepeatInterval;
import de.eldoria.companies.data.wrapper.series.SeriesLine;
import de.eldoria.companies.data.wrapper.series.SeriesState;
import de.eldoria.companies.data.wrapper.series.SeriesStopReason;
import de.eldoria.companies.data.wrapper.series.SeriesWait;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

class SqLiteSeriesDataTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 8, 30, 0);

    private final UUID owner = UUID.randomUUID();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private SqLiteSeriesData series;
    private SqLiteOrderData orders;

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(8);
        series = new SqLiteSeriesData(SqLiteTestDatabase.mapper(), executor);
        orders = new SqLiteOrderData(executor, SqLiteTestDatabase.mapper());
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static List<SeriesLine> lines() {
        return List.of(
                new SeriesLine(new ItemStack(Material.IRON_INGOT), false, 64, 2.5),
                new SeriesLine(new ItemStack(Material.DIAMOND_SWORD), true, 1, 100.0));
    }

    private OrderSeries save(UUID player, OptionalInt maxCopies, LocalDateTime now) {
        var draft = OrderSeries.draft(
                player, "Weekly iron", lines(), RepeatInterval.WEEKLY, maxCopies, Optional.empty(), 5, now);
        return series.submitSeries(draft).join().orElseThrow();
    }

    private OrderSeries save() {
        return save(owner, OptionalInt.of(4), NOW);
    }

    private OrderSeries reload(OrderSeries saved) {
        return series.retrieveSeries(saved.id()).join().orElseThrow();
    }

    private int saveOrder() {
        var contents = List.of(new OrderContent(0, new ItemStack(Material.IRON_INGOT), false, 64, 160.0));
        var player = SqLiteTestDatabase.player(owner);
        return orders.submitOrder(player, new SimpleOrder(owner, "Weekly iron", 1.0).toFullOrder(contents))
                .join()
                .orElseThrow()
                .id();
    }

    @Test
    void aSavedSeriesIsReadBackUnchanged() {
        var draft = OrderSeries.draft(
                owner,
                "Daily iron",
                lines(),
                RepeatInterval.DAILY,
                OptionalInt.empty(),
                Optional.of(NOW.plusDays(30)),
                7,
                NOW);

        var saved = series.submitSeries(draft).join().orElseThrow();
        var read = reload(saved);

        Assertions.assertTrue(saved.id() > 0);
        Assertions.assertEquals(saved, read);
        Assertions.assertEquals(
                Material.DIAMOND_SWORD, read.lines().get(1).template().getType());
        Assertions.assertTrue(read.lines().get(1).exact());
        Assertions.assertEquals(64, read.lines().getFirst().amount());
        Assertions.assertEquals(2.5, read.lines().getFirst().unitPrice());
        Assertions.assertEquals(Optional.of(NOW.plusDays(30)), read.ends());
        Assertions.assertTrue(read.maxCopies().isEmpty());
        Assertions.assertEquals(SeriesState.ACTIVE, read.state());
        Assertions.assertEquals(NOW, read.nextDue());
        Assertions.assertTrue(series.retrieveSeries(saved.id() + 1).join().isEmpty());
    }

    @Test
    void seriesAreListedPerOwnerNewestFirst() {
        var first = save(owner, OptionalInt.of(2), NOW);
        var second = save(owner, OptionalInt.of(2), NOW.plusMinutes(1));
        save(UUID.randomUUID(), OptionalInt.of(2), NOW);
        series.submitStop(first.id(), SeriesState.STOPPED, SeriesStopReason.OWNER, NOW)
                .join();

        Assertions.assertEquals(
                List.of(second.id(), first.id()),
                series.retrieveByOwner(owner, false).join().stream()
                        .map(OrderSeries::id)
                        .toList());
        Assertions.assertEquals(
                List.of(second.id()),
                series.retrieveByOwner(owner, true).join().stream()
                        .map(OrderSeries::id)
                        .toList());
        Assertions.assertEquals(1, series.retrieveActiveCount(owner).join());
    }

    @Test
    void onlyActiveSeriesThatAreDueAreSelected() {
        var due = save(owner, OptionalInt.of(2), NOW.minusHours(2));
        var dueLater = save(owner, OptionalInt.of(2), NOW.minusHours(1));
        save(owner, OptionalInt.of(2), NOW.plusMinutes(5));
        var stopped = save(owner, OptionalInt.of(2), NOW.minusHours(3));
        series.submitStop(stopped.id(), SeriesState.STOPPED, SeriesStopReason.OWNER, NOW)
                .join();

        Assertions.assertEquals(
                List.of(due.id(), dueLater.id()),
                series.retrieveDue(NOW, 10).join().stream().map(OrderSeries::id).toList());
        Assertions.assertEquals(1, series.retrieveDue(NOW, 1).join().size());
    }

    @Test
    void aDueCopyIsClaimedOnce() {
        var saved = save();
        var next = RepeatInterval.WEEKLY.next(saved.nextDue());

        Assertions.assertTrue(series.submitClaim(saved, next, NOW).join());
        Assertions.assertFalse(series.submitClaim(saved, next, NOW).join());

        var read = reload(saved);
        Assertions.assertEquals(1, read.posted());
        Assertions.assertEquals(next, read.nextDue());
        Assertions.assertTrue(series.retrieveDue(NOW, 10).join().isEmpty());
    }

    @Test
    void concurrentClaimsOfOneCopyLetOneWin() {
        var saved = save();
        var next = RepeatInterval.WEEKLY.next(saved.nextDue());
        List<CompletableFuture<Boolean>> claims = new ArrayList<>();
        for (var i = 0; i < 8; i++) claims.add(series.submitClaim(saved, next, NOW));

        var won = claims.stream()
                .map(CompletableFuture::join)
                .filter(Boolean::booleanValue)
                .count();

        Assertions.assertEquals(1, won);
        Assertions.assertEquals(1, reload(saved).posted());
    }

    @Test
    void aReleasedClaimRestoresTheSchedule() {
        var saved = save();
        series.submitClaim(saved, RepeatInterval.WEEKLY.next(saved.nextDue()), NOW)
                .join();

        Assertions.assertTrue(series.submitClaimRelease(saved, NOW).join());
        Assertions.assertFalse(series.submitClaimRelease(saved, NOW).join());

        var read = reload(saved);
        Assertions.assertEquals(0, read.posted());
        Assertions.assertEquals(saved.nextDue(), read.nextDue());
        Assertions.assertTrue(
                series.submitClaim(read, read.nextDue().plusDays(7), NOW).join());
    }

    @Test
    void stoppingWorksOnlyOnActiveSeries() {
        var saved = save();

        Assertions.assertTrue(series.submitStop(saved.id(), SeriesState.STOPPED, SeriesStopReason.CANNOT_PAY, NOW)
                .join());
        Assertions.assertFalse(
                series.submitStop(saved.id(), SeriesState.FINISHED, null, NOW).join());

        var read = reload(saved);
        Assertions.assertEquals(SeriesState.STOPPED, read.state());
        Assertions.assertEquals(Optional.of(SeriesStopReason.CANNOT_PAY), read.stopReason());
        Assertions.assertFalse(series.submitClaim(read, NOW.plusDays(7), NOW).join());
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> series.submitStop(saved.id(), SeriesState.ACTIVE, null, NOW));
    }

    @Test
    void finishedSeriesHaveNoStopReasonAndCannotBeResumed() {
        var saved = save();

        Assertions.assertTrue(
                series.submitStop(saved.id(), SeriesState.FINISHED, null, NOW).join());
        Assertions.assertFalse(series.submitResume(saved.id(), NOW, NOW).join());

        var read = reload(saved);
        Assertions.assertEquals(SeriesState.FINISHED, read.state());
        Assertions.assertTrue(read.stopReason().isEmpty());
    }

    @Test
    void aStoppedSeriesResumesWithANewDueTime() {
        var saved = save();
        series.submitWaiting(saved.id(), SeriesWait.ORDER_LIMIT, NOW).join();
        series.submitStop(saved.id(), SeriesState.STOPPED, SeriesStopReason.OWNER, NOW)
                .join();

        Assertions.assertTrue(
                series.submitResume(saved.id(), NOW.plusHours(1), NOW).join());
        Assertions.assertFalse(
                series.submitResume(saved.id(), NOW.plusHours(1), NOW).join());

        var read = reload(saved);
        Assertions.assertEquals(SeriesState.ACTIVE, read.state());
        Assertions.assertTrue(read.stopReason().isEmpty());
        Assertions.assertTrue(read.waiting().isEmpty());
        Assertions.assertEquals(NOW.plusHours(1), read.nextDue());
    }

    @Test
    void waitingIsReportedOncePerReasonAndClearedByAClaim() {
        var saved = save();

        Assertions.assertTrue(
                series.submitWaiting(saved.id(), SeriesWait.ORDER_LIMIT, NOW).join());
        Assertions.assertFalse(
                series.submitWaiting(saved.id(), SeriesWait.ORDER_LIMIT, NOW).join());
        Assertions.assertTrue(
                series.submitWaiting(saved.id(), SeriesWait.PREVIOUS_OPEN, NOW).join());
        Assertions.assertEquals(
                Optional.of(SeriesWait.PREVIOUS_OPEN), reload(saved).waiting());

        series.submitClaim(saved, saved.nextDue().plusDays(7), NOW).join();

        Assertions.assertTrue(reload(saved).waiting().isEmpty());
        Assertions.assertTrue(
                series.submitWaiting(saved.id(), SeriesWait.ORDER_LIMIT, NOW).join());
    }

    @Test
    void theOrderLimitIsRefreshedOnAllSeriesOfTheOwner() {
        var first = save();
        var second = save();
        var other = save(UUID.randomUUID(), OptionalInt.of(1), NOW);

        Assertions.assertEquals(2, series.submitOrderLimit(owner, 9).join());

        Assertions.assertEquals(9, reload(first).orderLimit());
        Assertions.assertEquals(9, reload(second).orderLimit());
        Assertions.assertEquals(5, reload(other).orderLimit());
    }

    @Test
    void theCurrentCopyIsLinkedToTheSeries() {
        var saved = save();
        var order = saveOrder();

        Assertions.assertTrue(series.submitCurrentOrder(saved.id(), order).join());
        Assertions.assertFalse(
                series.submitCurrentOrder(saved.id(), order + 100).join());

        var read = reload(saved);
        Assertions.assertEquals(OptionalInt.of(order), read.currentOrder());
        Assertions.assertEquals(
                saved.id(),
                orders.retrieveOrderById(order).join().orElseThrow().seriesId().orElseThrow());
    }

    @Test
    void theStateOfTheCurrentCopyIsRead() {
        var saved = save();
        Assertions.assertTrue(series.retrieveCurrentOrderState(saved).join().isEmpty());
        var order = saveOrder();
        series.submitCurrentOrder(saved.id(), order).join();

        Assertions.assertEquals(
                Optional.of(OrderState.UNCLAIMED),
                series.retrieveCurrentOrderState(reload(saved)).join());
        SqLiteTestDatabase.setState(order, OrderState.CLAIMED.stateId(), 1);
        Assertions.assertEquals(
                Optional.of(OrderState.CLAIMED),
                series.retrieveCurrentOrderState(reload(saved)).join());
        query("DELETE FROM order_states WHERE id = ?")
                .single(call().bind(order))
                .delete();
        Assertions.assertTrue(
                series.retrieveCurrentOrderState(reload(saved)).join().isEmpty());
    }
}
