/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings.Competition;
import de.eldoria.companies.configuration.elements.systemorders.SystemOrderSettings;
import de.eldoria.companies.dashboard.MutableClock;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.orders.system.SystemOrderService.Step;
import de.eldoria.companies.util.Times;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuessedOrdersTest {
    private static final int COMPANY = 3;

    @TempDir
    Path directory;

    private final SystemOrderSettings settings = new SystemOrderSettings();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-26T08:00:00Z"));
    private final Map<String, Double> averages = new HashMap<>();
    private final List<Integer> removed = new ArrayList<>();
    private SqLiteOrderData orderData;
    private OfflinePlayer system;
    private OfflinePlayer player;
    private SystemOrderService service;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        Times.useZone(() -> ZoneOffset.UTC);
        SqLiteTestDatabase.create(directory);
        orderData = new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
        system = SqLiteTestDatabase.player(SystemOwner.ID);
        player = SqLiteTestDatabase.player(UUID.randomUUID());
        settings.enabled(true);
        var guessed = settings.guessed();
        guessed.enabled(true);
        guessed.minOrders(1);
        guessed.jitter(0);
        guessed.share(0);
        guessed.minOpen(1);
        guessed.maxOpen(1);
        averages.put("STONE", 1.0);
        averages.put("DIRT", 2.0);
        delivered(Material.STONE, 128, 10.0, now().minusDays(1));
        delivered(Material.DIRT, 128, 10.0, now().minusDays(10));
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        service = new SystemOrderService(
                () -> settings,
                orderData,
                new FakePlacement(),
                new SystemOrderService.Funding() {
                    @Override
                    public boolean withdraw(String account, double amount) {
                        return true;
                    }

                    @Override
                    public void deposit(String account, double amount) {}
                },
                clock,
                () -> 0.5,
                logger);
    }

    @AfterEach
    void tearDown() {
        Times.useZone(Times::machineZone);
        MockBukkit.unmock();
    }

    @Test
    void theMaterialPlayersBuyLeastLatelyIsGuessedFirst() {
        var run = service.run();

        assertEquals(List.of(new SystemOrderService.Result("guessed DIRT", Step.PLACED)), run.rules());
        var record = only();
        assertEquals("DIRT", record.material());
        assertEquals(64, record.amount());
        assertEquals(1.6, record.unit());
        assertEquals(0, record.step());
        assertEquals(1, record.rank());
        assertEquals(2, record.candidates());
        assertNull(record.replaces());
    }

    @Test
    void aMaterialPlayersHaveOpenOrdersForIsSkipped() {
        open(Material.DIRT, 10, 30.0);

        service.run();

        assertEquals("STONE", only().material());
    }

    @Test
    void aGuessedOrderIsWithdrawnOncePlayersOrderItsMaterial() {
        service.run();
        var dirt = onlyDecision().order();

        open(Material.DIRT, 10, 30.0);
        var run = service.run();

        assertEquals(List.of(dirt), run.removed());
        assertEquals("STONE", only().material());
    }

    @Test
    void anUntakenOrderIsRepricedThenItsMaterialRests() {
        settings.guessed().repriceAfterHours(2);
        settings.guessed().maxReprices(1);
        settings.guessed().repriceStep(0.1);
        service.run();
        var first = onlyDecision().order();

        clock.advance(Duration.ofHours(2));
        service.run();
        var repriced = only();
        assertEquals(1, repriced.step());
        assertEquals(first, repriced.replaces());
        assertEquals(1.8, repriced.unit());

        clock.advance(Duration.ofHours(2));
        service.run();
        assertEquals("STONE", only().material());
        assertTrue(orderData.restingMaterials(now()).contains("DIRT"));
    }

    @Test
    void anUnderbiddingOrderPaysClearlyLessThanPlayers() {
        settings.guessed().competition(Competition.UNDERBID);
        open(Material.DIRT, 10, 10.0);

        service.run();

        var record = only();
        assertEquals("DIRT", record.material());
        assertEquals(0.7, record.cap());
        assertEquals(0.7, record.unit());
        assertEquals(10, record.playerItems());
    }

    @Test
    void anUnderbiddingOrderIsWithdrawnWhenPlayersPayLessThanItsMargin() {
        settings.guessed().competition(Competition.UNDERBID);
        service.run();
        var dirt = onlyDecision().order();

        open(Material.DIRT, 10, 10.0);
        var run = service.run();

        assertEquals(List.of(dirt), run.removed());
    }

    @Test
    void theShareOfOpenPlayerOrdersSetsHowManyAreGuessed() {
        settings.guessed().share(0.5);
        settings.guessed().minOpen(0);
        settings.guessed().maxOpen(10);

        assertEquals(List.of(), service.run().rules());
        open(Material.SAND, 1, 1.0);
        open(Material.SAND, 1, 1.0);

        assertEquals(2, service.run().rules().size());
    }

    @Test
    void whileDisabledOpenOrdersRunOutWithoutReplacement() {
        settings.guessed().repriceAfterHours(2);
        service.run();
        var dirt = onlyDecision().order();
        settings.guessed().enabled(false);

        clock.advance(Duration.ofHours(1));
        assertEquals(List.of(), service.run().removed());
        clock.advance(Duration.ofHours(1));
        var run = service.run();

        assertEquals(List.of(dirt), run.removed());
        assertTrue(run.rules().isEmpty());
        assertTrue(guessedDecisions().isEmpty());
    }

    @Test
    void aCompanyMayTakeOnlySoManyGuessedOrdersADay() {
        settings.guessed().perCompanyDaily(1);
        var limit = new GuessedLimit(orderData, settings::guessed, clock);
        var company = new CompanyProfile(COMPANY, "Acme", Times.now(), 1, CompanyDetails.NONE, List.of());
        service.run();
        var guessed = orderData.retrieveOrderById(onlyDecision().order()).join().orElseThrow();
        var playerOrder = open(Material.SAND, 1, 1.0);

        assertTrue(limit.refusal(company, guessed).isEmpty());
        SqLiteTestDatabase.setState(guessed.id(), OrderState.CLAIMED.stateId(), COMPANY);
        at(guessed.id(), now());

        assertTrue(limit.refusal(company, guessed).isPresent());
        assertTrue(limit.refusal(company, playerOrder).isEmpty());
        settings.guessed().perCompanyDaily(0);
        assertTrue(limit.refusal(company, guessed).isEmpty());
    }

    private GuessedRecord only() {
        return JsonMapper.builder().build().readValue(onlyDecision().record(), GuessedRecord.class);
    }

    private SystemDecision onlyDecision() {
        var decisions = guessedDecisions();
        assertEquals(1, decisions.size(), "one open guessed order");
        return decisions.getFirst();
    }

    private List<SystemDecision> guessedDecisions() {
        return orderData.systemDecisions(OrderState.UNCLAIMED, OrderState.UNCLAIMED).stream()
                .filter(decision -> decision.rule().equals(GuessedSettings.RULE))
                .toList();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void delivered(Material material, int amount, double price, LocalDateTime when) {
        var order = order(player, material, amount, price);
        SqLiteTestDatabase.setState(order.id(), OrderState.DELIVERED.stateId(), COMPANY);
        at(order.id(), when);
    }

    private SimpleOrder open(Material material, int amount, double price) {
        return order(player, material, amount, price);
    }

    private SimpleOrder order(OfflinePlayer owner, Material material, int amount, double price) {
        var content = new OrderContent(0, new ItemStack(material), false, amount, price);
        orderData
                .submitOrder(owner, new SimpleOrder(owner.getUniqueId(), "test", 0.0).toFullOrder(List.of(content)))
                .join();
        var orders = orderData
                .retrieveOrdersByPlayer(owner, OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join();
        return orders.get(orders.size() - 1);
    }

    private static void at(int order, LocalDateTime time) {
        query("UPDATE order_states SET last_update = ? WHERE id = ?")
                .single(call().bind(time, TimeColumns.SQLITE_TEXT).bind(order))
                .update();
    }

    private final class FakePlacement implements SystemOrderService.Placement {
        @Override
        public boolean knows(String material) {
            return true;
        }

        @Override
        public OptionalDouble average(String material) {
            var average = averages.get(material);
            return average == null ? OptionalDouble.empty() : OptionalDouble.of(average);
        }

        @Override
        public int stackSize(String material) {
            return 64;
        }

        @Override
        public CompletableFuture<SystemOrderService.Placed> place(SystemOrderService.Wanted wanted) {
            var total = OrderBuilder.total(wanted.unit(), wanted.amount());
            var order = order(system, Material.matchMaterial(wanted.material()), wanted.amount(), total);
            orderData.systemRule(order.id(), wanted.rule());
            at(order.id(), now());
            return CompletableFuture.completedFuture(new SystemOrderService.Created(order.id(), total));
        }

        @Override
        public boolean remove(int order) {
            removed.add(order);
            var found = orderData.retrieveOrderById(order).join();
            if (found.isEmpty() || found.get().state() != OrderState.UNCLAIMED) return false;
            SqLiteTestDatabase.setState(order, OrderState.RECEIVED.stateId(), 0);
            return true;
        }
    }
}
