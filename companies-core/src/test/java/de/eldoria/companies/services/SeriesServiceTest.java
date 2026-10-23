/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.IOrderDraft;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.companies.configuration.elements.ordersettings.OrderFee;
import de.eldoria.companies.configuration.elements.ordersettings.RepeatingSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteSeriesData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.series.OrderSeries;
import de.eldoria.companies.data.wrapper.series.RepeatInterval;
import de.eldoria.companies.data.wrapper.series.SeriesLine;
import de.eldoria.companies.data.wrapper.series.SeriesState;
import de.eldoria.companies.data.wrapper.series.SeriesStopReason;
import de.eldoria.companies.data.wrapper.series.SeriesWait;
import de.eldoria.companies.events.order.PreOrderCreateEvent;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderLimits;
import de.eldoria.companies.orders.Repetition;
import de.eldoria.companies.services.SeriesService.Decision;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 8, 30, 0);

    private final OrderSettings orderSettings = new OrderSettings() {
        @Override
        public OrderFee fee() {
            return new OrderFee(1.0, 10.0, 0.0, 0.0);
        }
    };
    private final UserSettings userSettings = new UserSettings();
    private final RepeatingSettings settings = new RepeatingSettings();
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final List<String> notices = new CopyOnWriteArrayList<>();
    private final MutableClock clock = new MutableClock(NOW);

    @TempDir
    Path directory;

    private ServerMock server;
    private Plugin plugin;
    private ExecutorService executor;
    private SqLiteSeriesData seriesData;
    private SqLiteOrderData orderData;
    private OfflinePlayer owner;
    private SeriesService service;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        seriesData = new SqLiteSeriesData(SqLiteTestDatabase.mapper(), executor);
        orderData = new SqLiteOrderData(executor, SqLiteTestDatabase.mapper());
        owner = server.getOfflinePlayer(UUID.randomUUID());
        balances.put(owner.getUniqueId(), 1000.0);
        userSettings.maxOrders(10);
        service = service(this::create);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    @Test
    void aDueCopyIsPostedAndCharged() {
        var saved = save(OptionalInt.of(4), Optional.empty(), 5);

        assertEquals(List.of(Decision.POSTED), service.runDue());

        var series = reload(saved);
        assertEquals(1, series.posted());
        assertEquals(NOW.plusWeeks(1), series.nextDue());
        assertEquals(SeriesState.ACTIVE, series.state());
        var copy = copy(series);
        assertEquals(OptionalInt.of(saved.id()), copy.seriesId());
        assertEquals(OrderState.UNCLAIMED, copy.state());
        assertEquals(1000.0 - 160.0 - 17.0, balance());
        assertEquals(List.of("posted " + saved.id() + " #1"), notices);
    }

    @Test
    void anOpenPreviousCopyHoldsTheNextOneBackSilently() {
        var saved = save(OptionalInt.of(4), Optional.empty(), 5);
        service.runDue();
        notices.clear();
        clock.set(NOW.plusWeeks(1));

        assertEquals(List.of(Decision.WAITING_PREVIOUS), service.runDue());

        var series = reload(saved);
        assertEquals(1, series.posted());
        assertEquals(Optional.of(SeriesWait.PREVIOUS_OPEN), series.waiting());
        assertTrue(notices.isEmpty());
    }

    @Test
    void anAcceptedPreviousCopyDoesNotHoldTheNextOneBack() {
        var saved = save(OptionalInt.of(4), Optional.empty(), 5);
        service.runDue();
        SqLiteTestDatabase.setState(reload(saved).currentOrder().getAsInt(), OrderState.CLAIMED.stateId(), 1);
        clock.set(NOW.plusWeeks(1));

        assertEquals(List.of(Decision.POSTED), service.runDue());
        assertEquals(2, reload(saved).posted());
    }

    @Test
    void theOrderLimitHoldsTheCopyBackAndTellsTheOwnerOnce() {
        var saved = save(OptionalInt.of(4), Optional.empty(), 1);
        otherOrder();

        assertEquals(List.of(Decision.WAITING_LIMIT), service.runDue());
        assertEquals(List.of(Decision.WAITING_LIMIT), service.runDue());

        var series = reload(saved);
        assertEquals(0, series.posted());
        assertEquals(Optional.of(SeriesWait.ORDER_LIMIT), series.waiting());
        assertEquals(SeriesState.ACTIVE, series.state());
        assertEquals(List.of("waiting " + saved.id()), notices);
    }

    @Test
    void missedIntervalsAreNotCaughtUp() {
        var saved = save(OptionalInt.empty(), Optional.empty(), 5);
        clock.set(NOW.plusDays(20));

        assertEquals(List.of(Decision.POSTED), service.runDue());

        var series = reload(saved);
        assertEquals(1, series.posted());
        assertEquals(NOW.plusWeeks(3), series.nextDue());
        assertEquals(List.of(), service.runDue());
    }

    @Test
    void anOwnerWhoCanNotPayStopsTheSeries() {
        var saved = save(OptionalInt.of(4), Optional.empty(), 5);
        balances.put(owner.getUniqueId(), 10.0);

        assertEquals(List.of(Decision.CANNOT_PAY), service.runDue());

        var series = reload(saved);
        assertEquals(SeriesState.STOPPED, series.state());
        assertEquals(Optional.of(SeriesStopReason.CANNOT_PAY), series.stopReason());
        assertEquals(0, series.posted());
        assertEquals(NOW, series.nextDue());
        assertEquals(10.0, balance());
        assertEquals(List.of("stopped " + saved.id()), notices);
    }

    @Test
    void theLastCopyByCountFinishesTheSeries() {
        var saved = save(OptionalInt.of(2), Optional.empty(), 5);
        service.runDue();
        removeCopy(saved);
        clock.set(NOW.plusWeeks(1));

        assertEquals(List.of(Decision.POSTED), service.runDue());

        var series = reload(saved);
        assertEquals(2, series.posted());
        assertEquals(SeriesState.FINISHED, series.state());
        assertEquals(Optional.empty(), series.stopReason());
        assertEquals(
                List.of("posted " + saved.id() + " #1", "posted " + saved.id() + " #2", "finished " + saved.id()),
                notices);
    }

    @Test
    void aSeriesPastItsEndDateFinishesWithoutPosting() {
        var saved = save(OptionalInt.empty(), Optional.of(NOW.plusDays(10)), 5);
        service.runDue();
        removeCopy(saved);
        clock.set(NOW.plusDays(11));

        assertEquals(List.of(Decision.FINISHED), service.runDue());

        var series = reload(saved);
        assertEquals(1, series.posted());
        assertEquals(SeriesState.FINISHED, series.state());
    }

    @Test
    void aCopyWhoseFollowerWouldBeDueAfterTheEndDateIsTheLast() {
        var saved = save(OptionalInt.empty(), Optional.of(NOW.plusDays(5)), 5);

        assertEquals(List.of(Decision.POSTED), service.runDue());

        assertEquals(SeriesState.FINISHED, reload(saved).state());
        assertEquals("finished " + saved.id(), notices.get(notices.size() - 1));
    }

    @Test
    void aCancelledPreEventSkipsOneCopyAndKeepsTheSchedule() {
        var saved = save(OptionalInt.of(4), Optional.empty(), 5);
        server.getPluginManager()
                .registerEvent(
                        PreOrderCreateEvent.class,
                        new Listener() {},
                        EventPriority.NORMAL,
                        (listener, event) -> {
                            if (event instanceof PreOrderCreateEvent pre) pre.setCancelled(true);
                        },
                        plugin);

        assertEquals(List.of(Decision.SKIPPED), service.runDue());

        var series = reload(saved);
        assertEquals(1, series.posted());
        assertEquals(NOW.plusWeeks(1), series.nextDue());
        assertEquals(SeriesState.ACTIVE, series.state());
        assertEquals(OptionalInt.empty(), series.currentOrder());
        assertEquals(1000.0, balance());
    }

    @Test
    void aFailedSaveReleasesTheClaim() {
        var failing = service((id, draft) -> CompletableFuture.failedFuture(CommandFailure.of("error.saveFailed")));
        var saved = save(OptionalInt.of(4), Optional.empty(), 5);

        assertEquals(List.of(Decision.FAILED), failing.runDue());

        var series = reload(saved);
        assertEquals(0, series.posted());
        assertEquals(NOW, series.nextDue());
        assertEquals(SeriesState.ACTIVE, series.state());
    }

    @Test
    void aDueCopyIsClaimedOnce() {
        var saved = save(OptionalInt.of(4), Optional.empty(), 5);
        var other = service(this::create);

        var first = service.postDue(saved, NOW);
        var second = other.postDue(saved, NOW);
        var decisions = List.of(first.join(), second.join());

        assertTrue(decisions.contains(Decision.POSTED), decisions.toString());
        assertTrue(decisions.contains(Decision.LOST_CLAIM), decisions.toString());
        assertEquals(1, orderData.retrievePlayerOrderCount(owner).join());
        assertEquals(1, reload(saved).posted());
    }

    @Test
    void nothingIsPostedWhileRepeatingOrdersAreDisabled() {
        var saved = save(OptionalInt.of(4), Optional.empty(), 5);
        settings.enabled(false);

        assertEquals(List.of(), service.runDue());
        assertEquals(0, reload(saved).posted());
    }

    @Test
    void cancellingACopyStopsTheSeries() {
        var saved = save(OptionalInt.of(4), Optional.empty(), 5);
        service.runDue();
        var copy = copy(reload(saved));

        assertTrue(service.onCopyCancelled(copy).join());
        assertFalse(service.onCopyCancelled(copy).join());

        var series = reload(saved);
        assertEquals(SeriesState.STOPPED, series.state());
        assertEquals(Optional.of(SeriesStopReason.COPY_CANCELLED), series.stopReason());
    }

    @Test
    void cancellingAOneOffOrderStopsNothing() {
        var saved = save(OptionalInt.of(4), Optional.empty(), 5);

        assertFalse(service.onCopyCancelled(otherOrder()).join());
        assertEquals(SeriesState.ACTIVE, reload(saved).state());
    }

    @Test
    void anAdminRemovingACopyStopsTheSeriesAndTellsTheOwner() {
        var saved = save(OptionalInt.of(4), Optional.empty(), 5);
        service.runDue();
        notices.clear();

        assertTrue(service.onCopyRemovedByAdmin(copy(reload(saved))).join());

        assertEquals(Optional.of(SeriesStopReason.ADMIN), reload(saved).stopReason());
        assertEquals(List.of("stopped " + saved.id()), notices);
    }

    @Test
    void startingPostsTheFirstCopyAtOnce() {
        var player = player();

        var created = service.start(player, draft(player), Repetition.times(RepeatInterval.DAILY, 3))
                .join();

        var series = seriesData.retrieveByOwner(player.getUniqueId(), false).join();
        assertEquals(1, series.size());
        var started = series.get(0);
        assertEquals(1, started.posted());
        assertEquals(NOW.plusDays(1), started.nextDue());
        assertEquals(OptionalInt.of(created.order().id()), started.currentOrder());
        assertEquals(OptionalInt.of(3), started.maxCopies());
        assertEquals(1000.0 - 160.0 - 17.0, balances.get(player.getUniqueId()));
        assertTrue(notices.isEmpty());
        assertEquals(List.of(), service.runDue());
    }

    @Test
    void aFirstCopyThatCanNotBePaidStopsTheNewSeries() {
        var player = player();
        balances.put(player.getUniqueId(), 5.0);

        var failure = failure(service.start(player, draft(player), Repetition.times(RepeatInterval.DAILY, 3)));

        assertEquals("error.insufficientCurrency", failure.localeKey());
        var started =
                seriesData.retrieveByOwner(player.getUniqueId(), false).join().get(0);
        assertEquals(SeriesState.STOPPED, started.state());
        assertEquals(Optional.of(SeriesStopReason.CANNOT_PAY), started.stopReason());
        assertEquals(0, started.posted());
    }

    @Test
    void startingNeedsThePermissionAndRoomForAnotherSeries() {
        var player = server.addPlayer();
        balances.put(player.getUniqueId(), 1000.0);
        var repetition = Repetition.times(RepeatInterval.DAILY, 3);

        assertEquals(
                "error.permission.repeatOrder",
                failure(service.start(player, draft(player), repetition)).localeKey());

        player.addAttachment(plugin, Permission.Orders.REPEAT, true);
        settings.maxSeriesPerPlayer(0);
        assertEquals(
                "order.draft.repeat.error.maxSeries",
                failure(service.start(player, draft(player), repetition)).localeKey());
    }

    @Test
    void aStoppedSeriesResumesOnlyBeforeItsEnd() {
        var player = player();
        service.start(player, draft(player), Repetition.times(RepeatInterval.DAILY, 3))
                .join();
        var started =
                seriesData.retrieveByOwner(player.getUniqueId(), false).join().get(0);

        service.stop(player.getUniqueId(), started.id()).join();
        assertEquals(
                "order.series.error.notActive",
                failure(service.stop(player.getUniqueId(), started.id())).localeKey());
        service.resume(player, started.id()).join();

        var resumed = reload(started);
        assertEquals(SeriesState.ACTIVE, resumed.state());
        assertEquals(NOW, resumed.nextDue());
        assertEquals(
                "order.series.error.notStopped",
                failure(service.resume(player, started.id())).localeKey());
        assertEquals(
                "order.series.error.unknown",
                failure(service.stop(UUID.randomUUID(), started.id())).localeKey());
    }

    private SeriesService service(SeriesService.Copies copies) {
        return new SeriesService(
                seriesData,
                copies,
                id -> orderData
                        .retrievePlayerOrderCount(server.getOfflinePlayer(id))
                        .asFuture(),
                player -> 5,
                new RecordingNotices(),
                () -> settings,
                clock,
                plugin.getLogger());
    }

    private CompletableFuture<OrderCreationService.Outcome> create(UUID id, IOrderDraft draft) {
        var creation = new OrderCreationService(
                orderData,
                () -> orderSettings,
                new OrderLimits(orderData, () -> userSettings),
                item -> Optional.empty(),
                new Wallet(),
                server.getPluginManager()::callEvent,
                Runnable::run,
                plugin.getLogger());
        return creation.create(server.getOfflinePlayer(id), draft, OrderSource.REPEATING, true, false);
    }

    private OrderSeries save(OptionalInt maxCopies, Optional<LocalDateTime> ends, int orderLimit) {
        var draft = OrderSeries.draft(
                owner.getUniqueId(),
                "Weekly stone",
                List.of(new SeriesLine(new ItemStack(Material.STONE), false, 64, 2.5)),
                RepeatInterval.WEEKLY,
                maxCopies,
                ends,
                orderLimit,
                NOW);
        return seriesData.submitSeries(draft).join().orElseThrow();
    }

    private OrderSeries reload(OrderSeries series) {
        return seriesData.retrieveSeries(series.id()).join().orElseThrow();
    }

    private SimpleOrder copy(OrderSeries series) {
        return orderData
                .retrieveOrderById(series.currentOrder().getAsInt())
                .join()
                .orElseThrow();
    }

    private void removeCopy(OrderSeries series) {
        var copy = copy(reload(series));
        assertTrue(orderData.submitOrderDeletion(copy).join());
    }

    private SimpleOrder otherOrder() {
        var contents = List.of(new OrderContent(0, new ItemStack(Material.DIRT), false, 1, 1.0));
        return orderData
                .submitOrder(owner, new SimpleOrder(owner.getUniqueId(), "Other", 0.0).toFullOrder(contents))
                .join()
                .orElseThrow();
    }

    private PlayerMock player() {
        var player = server.addPlayer();
        player.addAttachment(plugin, Permission.Orders.REPEAT, true);
        balances.put(player.getUniqueId(), 1000.0);
        return player;
    }

    private static OrderBuilder draft(PlayerMock player) {
        var draft = new OrderBuilder(player.getUniqueId(), "Daily stone");
        draft.add(new ItemStack(Material.STONE), false, 64, 2.5);
        return draft;
    }

    private static CommandFailure failure(CompletableFuture<?> future) {
        var err = assertThrows(CompletionException.class, future::join);
        return assertInstanceOf(CommandFailure.class, err.getCause());
    }

    private double balance() {
        return balances.get(owner.getUniqueId());
    }

    private final class RecordingNotices implements SeriesService.Notices {
        @Override
        public void posted(OrderSeries series, OrderCreationService.Created copy, int number) {
            notices.add("posted " + series.id() + " #" + number);
        }

        @Override
        public void waitingLimit(OrderSeries series) {
            notices.add("waiting " + series.id());
        }

        @Override
        public void stopped(OrderSeries series) {
            notices.add("stopped " + series.id());
        }

        @Override
        public void finished(OrderSeries series) {
            notices.add("finished " + series.id());
        }
    }

    private final class Wallet implements OrderCreationService.Wallet {
        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            var balance = balances.getOrDefault(player.getUniqueId(), 0.0);
            if (balance < amount) return false;
            balances.put(player.getUniqueId(), balance - amount);
            return true;
        }

        @Override
        public void deposit(OfflinePlayer player, double amount) {
            balances.merge(player.getUniqueId(), amount, Double::sum);
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }

        @Override
        public String currencyName() {
            return "coins";
        }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant instant;

        private MutableClock(LocalDateTime time) {
            set(time);
        }

        private void set(LocalDateTime time) {
            instant = time.toInstant(ZoneOffset.UTC);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
