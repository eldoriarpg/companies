/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.api.write.RejectReason;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.components.event.EventState;
import de.eldoria.companies.components.order.IOrderDraft;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.components.order.LineKind;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.companies.configuration.elements.ordersettings.OrderFee;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.events.order.OrderCreatedEvent;
import de.eldoria.companies.events.order.PreOrderCreateEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
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

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderCreationServiceTest {
    private final UserSettings userSettings = new UserSettings();
    private final OrderSettings settings = new OrderSettings() {
        @Override
        public OrderFee fee() {
            return new OrderFee(1.0, 10.0, 0.0, 0.0);
        }
    };
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final List<OrderCreatedEvent> created = new CopyOnWriteArrayList<>();

    @TempDir
    Path directory;

    private ServerMock server;
    private Plugin plugin;
    private SqLiteOrderData orderData;
    private OfflinePlayer owner;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        orderData = new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
        owner = server.getOfflinePlayer(UUID.randomUUID());
        balances.put(owner.getUniqueId(), 1000.0);
        settings.maxMaterials(2);
        settings.maxItems(100);
        userSettings.maxOrders(2);
        listen(OrderCreatedEvent.class, created::add);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aSystemOrderBelongsToTheServerChargesNothingAndKeepsItsRule() {
        var systemOrders =
                new SystemOrders(service(orderData), orderData, id -> server.getOfflinePlayer(id), plugin.getLogger());

        var outcome = systemOrders.place(stone(10, 5.0), "harbour").join();

        var order = assertInstanceOf(OrderCreationService.Created.class, outcome);
        assertEquals(SystemOwner.ID, order.order().owner());
        assertEquals(0.0, order.order().fee());
        assertEquals(OrderSource.SYSTEM, created.getLast().source());
        assertEquals(Optional.of("harbour"), orderData.systemRule(order.order().id()));
        assertTrue(order.order().charged());
        assertEquals(Optional.empty(), orderData.systemRule(order.order().id() + 1));
    }

    @Test
    void chargedOrderTakesPriceAndFee() {
        var outcome = create(stone(10, 5.0), OrderSource.API, true, true);

        var order = assertInstanceOf(OrderCreationService.Created.class, outcome);
        assertEquals(50.0, order.price());
        assertEquals(6.0, order.fee());
        assertEquals(944.0, balance());
        assertEquals(6.0, order.order().fee());
        assertEquals(OrderState.UNCLAIMED, order.order().state());
        assertTrue(order.order().charged());
        assertEquals(1, created.size());
        assertEquals(OrderSource.API, created.get(0).source());
        assertEquals(order.order().id(), created.get(0).order().id());
    }

    @Test
    void chargedOrderPaysTheFeeLessTheReductionsOfItsMaterialsAndStoresIt() {
        var service =
                service(orderData, reductions(feeReduction(30, "STONE"), feeReduction(20), feeReduction(90, "DIRT")));

        var outcome = service.create(owner, stone(10, 5.0), OrderSource.API, true, true)
                .join();

        var order = assertInstanceOf(OrderCreationService.Created.class, outcome);
        assertEquals(3.0, order.fee());
        assertEquals(3.0, order.order().fee());
        assertEquals(947.0, balance());
    }

    @Test
    void aFeeReductionAppliesToACriteriaLineAcceptingItsMaterial() {
        var service = service(orderData, reductions(feeReduction(50, "SPRUCE_LOG")));
        var draft = IOrderDraft.of("logs");
        draft.addLine(ItemCriteria.of("BIRCH_LOG", "SPRUCE_LOG"), 10, 5.0);

        var quote = assertInstanceOf(OrderCreationService.Priced.class, service.quote(owner, draft));
        var outcome = service.create(owner, draft, OrderSource.API, true, true).join();

        assertEquals(3.0, quote.fee());
        assertEquals(
                3.0,
                assertInstanceOf(OrderCreationService.Created.class, outcome)
                        .order()
                        .fee());
        assertEquals(947.0, balance());
    }

    @Test
    void feeReductionsAboveAllOfTheFeeChargeNoFee() {
        var service = service(orderData, reductions(feeReduction(70), feeReduction(60)));

        var outcome = service.create(owner, stone(10, 5.0), OrderSource.API, true, true)
                .join();

        assertEquals(
                0.0,
                assertInstanceOf(OrderCreationService.Created.class, outcome)
                        .order()
                        .fee());
        assertEquals(950.0, balance());
    }

    @Test
    void serverFundedOrderChargesNothingAndStoresNoFee() {
        var outcome = create(stone(10, 5.0), OrderSource.API, false, true);

        var order = assertInstanceOf(OrderCreationService.Created.class, outcome);
        assertEquals(0.0, order.fee());
        assertEquals(0.0, order.order().fee());
        assertEquals(50.0, order.order().price());
        assertEquals(1000.0, balance());
        assertFalse(order.order().charged());
        assertFalse(orderData
                .retrieveOrderById(order.order().id())
                .join()
                .orElseThrow()
                .charged());
    }

    @Test
    void ownerWhoCanNotPayIsRejected() {
        balances.put(owner.getUniqueId(), 10.0);

        var rejected = rejected(create(stone(10, 5.0), OrderSource.PLAYER, true, true));

        assertEquals(RejectReason.INSUFFICIENT_FUNDS, rejected.reason());
        assertEquals("error.insufficientCurrency", rejected.failure().localeKey());
        assertEquals(10.0, balance());
        assertEquals(0, orderCount());
    }

    @Test
    void cancelledPreEventKeepsTheListenersReason() {
        var reason = Component.text("Not today");
        listen(PreOrderCreateEvent.class, event -> event.cancel(reason));

        var rejected = rejected(create(stone(10, 5.0), OrderSource.PLAYER, true, true));

        assertEquals(RejectReason.CANCELLED, rejected.reason());
        assertEquals(Optional.of(reason), rejected.failure().message());
        assertEquals(1000.0, balance());
        assertEquals(0, orderCount());
        assertTrue(created.isEmpty());
    }

    @Test
    void preEventCancelledWithoutReasonUsesTheGenericMessage() {
        listen(PreOrderCreateEvent.class, event -> event.setCancelled(true));

        var rejected = rejected(create(stone(10, 5.0), OrderSource.API, true, true));

        assertEquals(RejectReason.CANCELLED, rejected.reason());
        assertEquals("error.cancelledByIntegration", rejected.failure().localeKey());
        assertTrue(rejected.failure().message().isEmpty());
    }

    @Test
    void preEventSeesSourceAndOwner() {
        var seen = new CopyOnWriteArrayList<PreOrderCreateEvent>();
        listen(PreOrderCreateEvent.class, seen::add);

        create(stone(1, 1.0), OrderSource.PLAYER, true, true);

        assertEquals(1, seen.size());
        assertEquals(OrderSource.PLAYER, seen.get(0).source());
        assertEquals(owner, seen.get(0).owner());
    }

    @Test
    void adjustedPriceRecomputesTheFee() {
        listen(PreOrderCreateEvent.class, event -> event.draft().lines().get(0).unitPrice(10.0));

        var order = assertInstanceOf(
                OrderCreationService.Created.class, create(stone(10, 5.0), OrderSource.API, true, true));

        assertEquals(100.0, order.price());
        assertEquals(11.0, order.fee());
        assertEquals(889.0, balance());
        assertEquals(100.0, order.order().price());
    }

    @Test
    void adjustmentOverTheLimitsIsRejectedAsInvalid() {
        listen(PreOrderCreateEvent.class, event -> {
            event.draft().addLine(Material.DIRT, 1, 1.0);
            event.draft().addLine(Material.SAND, 1, 1.0);
        });

        var rejected = rejected(create(stone(10, 5.0), OrderSource.API, true, true));

        assertEquals(RejectReason.INVALID, rejected.reason());
        assertEquals("error.integrationDraftInvalid", rejected.failure().localeKey());
        assertEquals(1000.0, balance());
        assertEquals(0, orderCount());
    }

    @Test
    void draftLeftWithoutLinesIsRejectedAsInvalid() {
        listen(PreOrderCreateEvent.class, event -> event.draft().lines().forEach(line -> line.remove()));

        var rejected = rejected(create(stone(10, 5.0), OrderSource.API, true, true));

        assertEquals(RejectReason.INVALID, rejected.reason());
        assertEquals(1000.0, balance());
    }

    @Test
    void tooManyLinesAreRejectedOnlyWhenLimitsApply() {
        var draft = stone(1, 1.0);
        draft.addLine(Material.DIRT, 1, 1.0);
        draft.addLine(Material.SAND, 1, 1.0);

        assertEquals(
                RejectReason.TOO_MANY_LINES,
                rejected(create(draft, OrderSource.API, true, true)).reason());
        assertInstanceOf(OrderCreationService.Created.class, create(draft, OrderSource.API, true, false));
    }

    @Test
    void tooManyItemsAreRejected() {
        assertEquals(
                RejectReason.TOO_MANY_ITEMS,
                rejected(create(stone(101, 1.0), OrderSource.API, true, true)).reason());
    }

    @Test
    void activeOrderLimitAppliesToOfflineOwners() {
        create(stone(1, 1.0), OrderSource.API, true, true);
        create(stone(1, 1.0), OrderSource.API, true, true);

        var rejected = rejected(create(stone(1, 1.0), OrderSource.API, true, true));

        assertEquals(RejectReason.LIMIT_REACHED, rejected.reason());
        assertInstanceOf(OrderCreationService.Created.class, create(stone(1, 1.0), OrderSource.API, true, false));
    }

    @Test
    void tooLongNameIsInvalid() {
        var draft = IOrderDraft.of("x".repeat(OrderDraftService.MAX_NAME_LENGTH));
        draft.addLine(Material.STONE, 1, 1.0);

        assertEquals(
                RejectReason.INVALID,
                rejected(create(draft, OrderSource.API, true, false)).reason());
    }

    @Test
    void failedSaveRefundsTheOwner() {
        var failing = new SqLiteOrderData(null, SqLiteTestDatabase.mapper()) {
            @Override
            protected Optional<Integer> putOrder(OfflinePlayer player, FullOrder order) {
                return Optional.empty();
            }
        };

        var future = service(failing).create(owner, stone(10, 5.0), OrderSource.API, true, true);

        var err = assertThrows(CompletionException.class, future::join);
        var failure = assertInstanceOf(CommandFailure.class, err.getCause());
        assertEquals("error.saveFailed", failure.localeKey());
        assertEquals(1000.0, balance());
        assertTrue(created.isEmpty());
    }

    @Test
    void exactLinesGoThroughTheItemMatcher() {
        var draft = IOrderDraft.of("exact");
        draft.addLine(new ItemStack(Material.DIAMOND_SWORD), 1, 1.0);

        var rejected = rejected(create(draft, OrderSource.API, true, true));

        assertEquals(RejectReason.INVALID, rejected.reason());
        assertEquals("order.draft.error.notOrderable", rejected.failure().localeKey());
    }

    @Test
    void criteriaLinesAreCreatedWithTheirCriteria() {
        var logs = ItemCriteria.of("#minecraft:logs");
        var draft = IOrderDraft.of("logs");
        draft.addLine(logs, 32, 2.0);
        draft.addLine(logs, 32, 3.0);

        var order = assertInstanceOf(OrderCreationService.Created.class, create(draft, OrderSource.API, true, true));

        var contents = orderData.retrieveFullOrder(order.order()).join().contents();
        assertEquals(1, contents.size());
        assertEquals(LineKind.CRITERIA, contents.getFirst().kind());
        assertEquals(logs, contents.getFirst().criteria().orElseThrow());
        assertEquals(64, contents.getFirst().amount());
        assertEquals(192.0, contents.getFirst().price());
    }

    @Test
    void criteriaNoItemCanMeetAreRejectedAsInvalid() {
        var draft = IOrderDraft.of("logs");
        draft.addLine(ItemCriteria.of("#minecraft:logs").withEnchantment("sharpness", 1), 10, 1.0);

        var rejected = rejected(create(draft, OrderSource.API, true, false));

        assertEquals(RejectReason.INVALID, rejected.reason());
        assertEquals(
                "order.criteria.error.enchantmentNotApplicable",
                rejected.failure().localeKey());
        assertEquals(0, orderCount());
    }

    @Test
    void aCriteriaLineCountsAsOneMaterial() {
        var draft = IOrderDraft.of("logs");
        draft.addLine(ItemCriteria.of("#minecraft:logs", "#minecraft:planks"), 10, 1.0);
        draft.addLine(Material.STONE, 10, 1.0);

        assertInstanceOf(OrderCreationService.Created.class, create(draft, OrderSource.API, true, true));
    }

    private OrderCreationService.Outcome create(
            IOrderDraft draft, OrderSource source, boolean charge, boolean respectLimits) {
        return service(orderData)
                .create(owner, draft, source, charge, respectLimits)
                .join();
    }

    private OrderCreationService service(SqLiteOrderData data) {
        return new OrderCreationService(
                data,
                () -> settings,
                new OrderLimits(data, () -> userSettings),
                item -> Optional.empty(),
                new Wallet(),
                server.getPluginManager()::callEvent,
                Runnable::run,
                plugin.getLogger());
    }

    private OrderCreationService service(SqLiteOrderData data, FeeReductions reductions) {
        return new OrderCreationService(
                data,
                () -> settings,
                new OrderLimits(data, () -> userSettings),
                item -> Optional.empty(),
                new Wallet(),
                server.getPluginManager()::callEvent,
                Runnable::run,
                plugin.getLogger(),
                reductions,
                Runnable::run);
    }

    private static FeeReductions reductions(ServerEvent... events) {
        return materials -> Arrays.stream(events)
                .filter(event -> event.matches(materials, OptionalInt.empty()))
                .toList();
    }

    private static ServerEvent feeReduction(double percent, String... materials) {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        return new ServerEvent(
                1,
                "Cheap week",
                EventKind.FEE_REDUCTION,
                percent,
                List.of(materials),
                List.of(),
                now.minusHours(1),
                now.plusHours(1),
                null,
                null,
                null,
                0,
                EventState.ACTIVE,
                null,
                "Nora",
                now,
                null,
                null);
    }

    private static IOrderDraft stone(int amount, double unitPrice) {
        var draft = IOrderDraft.of("stone");
        draft.addLine(Material.STONE, amount, unitPrice);
        return draft;
    }

    private static OrderCreationService.Rejected rejected(OrderCreationService.Outcome outcome) {
        return assertInstanceOf(OrderCreationService.Rejected.class, outcome);
    }

    private double balance() {
        return balances.get(owner.getUniqueId());
    }

    private int orderCount() {
        return orderData.retrievePlayerOrderCount(owner).join();
    }

    private <T extends Event> void listen(Class<T> type, Consumer<T> handler) {
        server.getPluginManager()
                .registerEvent(
                        type,
                        new Listener() {},
                        EventPriority.NORMAL,
                        (listener, event) -> {
                            if (type.isInstance(event)) handler.accept(type.cast(event));
                        },
                        plugin);
    }

    private final class Wallet implements OrderCreationService.Wallet {
        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            var balance = balances.get(player.getUniqueId());
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
}
