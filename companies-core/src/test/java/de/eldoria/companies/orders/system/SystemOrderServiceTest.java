/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.configuration.elements.systemorders.RulePrice;
import de.eldoria.companies.configuration.elements.systemorders.RulePrice.PriceMode;
import de.eldoria.companies.configuration.elements.systemorders.SystemOrderRule;
import de.eldoria.companies.configuration.elements.systemorders.SystemOrderSettings;
import de.eldoria.companies.dashboard.MutableClock;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.orders.system.SystemOrderService.Step;
import de.eldoria.companies.util.Times;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemOrderServiceTest {
    private static final String BANK = "Royal bank";

    @TempDir
    Path directory;

    private final SystemOrderSettings settings = new SystemOrderSettings();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-26T08:00:00Z"));
    private final Map<String, Double> balances = new HashMap<>();
    private final Map<String, Double> averages = new HashMap<>();
    private final List<Integer> removed = new ArrayList<>();
    private final List<String> logged = new CopyOnWriteArrayList<>();
    private OrderTable system;
    private String refusal;
    private Double changedPrice;
    private boolean failPlacement;
    private boolean failStore;
    private boolean failRemove;
    private int placements;
    private SystemOrderService service;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        Times.useZone(() -> ZoneOffset.UTC);
        SqLiteTestDatabase.create(directory);
        var orderData = new SqLiteOrderData(null, SqLiteTestDatabase.mapper()) {
            @Override
            public boolean saveSystemDecision(SystemDecision decision) {
                return !failStore && super.saveSystemDecision(decision);
            }
        };
        system = new OrderTable(orderData, SqLiteTestDatabase.player(SystemOwner.ID));
        settings.enabled(true);
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        service = new SystemOrderService(
                () -> settings, system.orderData(), new FakePlacement(), new FakeFunding(), clock, () -> 0.5, logger);
    }

    @AfterEach
    void tearDown() {
        Times.useZone(Times::machineZone);
        MockBukkit.unmock();
    }

    @Test
    void aDueRulePlacesAnOrderAndStoresWhy() {
        rule(fixed("iron", 2.0));

        var run = service.run();

        assertEquals(List.of(new SystemOrderService.Result("iron", Step.PLACED)), run.rules());
        var decision = system.orderData()
                .systemDecisions(OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .getFirst();
        assertEquals("iron", decision.rule());
        assertEquals(DefinedRecord.KIND, decision.kind());
        assertEquals(128.0, decision.paid());
        assertNull(decision.account());
        var record = JsonMapper.builder().build().readValue(decision.record(), DefinedRecord.class);
        assertEquals(2.0, record.unit());
        assertEquals(64, record.amount());
        assertEquals(SourceType.MINTED, record.source());
        assertEquals(10000.0 - 128.0, record.mintedLeft());
    }

    @Test
    void aRuleWaitsForItsIntervalAndItsOpenOrders() {
        var rule = rule(fixed("iron", 2.0));
        rule.lifetimeHours(48);
        service.run();

        assertEquals(Step.NOT_DUE, step(service.run()));
        clock.advance(Duration.ofHours(24));
        assertEquals(Step.FULL, step(service.run()));
        rule.maxOpen(2);
        assertEquals(Step.PLACED, step(service.run()));
        assertEquals(2, placements);
    }

    @Test
    void theMintedCapStopsNewOrders() {
        settings.mintedDailyCap(100);
        rule(fixed("iron", 2.0));

        assertEquals(Step.MINTED_CAP, step(service.run()));
        assertEquals(0, placements);
    }

    @Test
    void theDailyBudgetCountsWhatTheRulePlacedThatDay() {
        var rule = rule(fixed("iron", 2.0));
        rule.everyHours(1);
        rule.maxOpen(5);
        rule.source(new MoneySource(SourceType.MINTED, "", 200));

        assertEquals(Step.PLACED, step(service.run()));
        clock.advance(Duration.ofHours(1));
        assertEquals(Step.BUDGET, step(service.run()));
        clock.advance(Duration.ofHours(16));
        assertEquals(Step.PLACED, step(service.run()));
    }

    @Test
    void anAccountPaysWhenTheOrderIsPlaced() {
        balances.put(BANK, 1000.0);
        rule(fixed("iron", 2.0)).source(new MoneySource(SourceType.ACCOUNT, BANK, 0));

        assertEquals(Step.PLACED, step(service.run()));

        assertEquals(872.0, balances.get(BANK));
        var decision = system.orderData()
                .systemDecisions(OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .getFirst();
        assertEquals(BANK, decision.account());
    }

    @Test
    void anAccountThatCannotPayPlacesNothing() {
        balances.put(BANK, 10.0);
        rule(fixed("iron", 2.0)).source(new MoneySource(SourceType.ACCOUNT, BANK, 0));

        assertEquals(Step.CANNOT_PAY, step(service.run()));

        assertEquals(0, placements);
        assertEquals(10.0, balances.get(BANK));
    }

    @Test
    void aRefusedOrderGivesTheMoneyBack() {
        balances.put(BANK, 1000.0);
        refusal = "CANCELLED";
        rule(fixed("iron", 2.0)).source(new MoneySource(SourceType.ACCOUNT, BANK, 0));

        assertEquals(Step.REFUSED, step(service.run()));

        assertEquals(1000.0, balances.get(BANK));
    }

    @Test
    void anOrderPastItsLifetimeIsRemovedAndItsAccountPaidBack() {
        balances.put(BANK, 1000.0);
        rule(fixed("iron", 2.0)).source(new MoneySource(SourceType.ACCOUNT, BANK, 0));
        service.run();
        var order = system.orderData()
                .systemDecisions(OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .getFirst()
                .order();

        clock.advance(Duration.ofHours(11));
        assertEquals(List.of(), service.run().removed());
        clock.advance(Duration.ofHours(1));
        var run = service.run();

        assertEquals(List.of(order), run.removed());
        assertEquals(1000.0, balances.get(BANK));
    }

    @Test
    void ordersOfTheMintedKindAreRemovedWithoutPayingAnyoneBack() {
        rule(fixed("iron", 2.0));
        service.run();

        clock.advance(Duration.ofHours(12));
        var run = service.run();

        assertEquals(1, run.removed().size());
        assertTrue(balances.isEmpty());
    }

    @Test
    void whileDisabledNothingIsPlaced() {
        settings.enabled(false);
        rule(fixed("iron", 2.0));

        assertEquals(List.of(), service.run().rules());
        assertEquals(0, placements);
    }

    @Test
    void rulesWithoutUsableIdOrItemAreSkipped() {
        rule(fixed("iron", 2.0));
        rule(fixed("iron", 3.0));
        var unknown = new SystemOrderRule("mystery", "NOT_AN_ITEM");
        unknown.price(new RulePrice(PriceMode.FIXED, 1, 1, 0, 0, 0));
        settings.rules(append(unknown));

        var steps = service.run().rules().stream()
                .map(SystemOrderService.Result::step)
                .toList();

        assertEquals(List.of(Step.PLACED, Step.INVALID_ID, Step.UNKNOWN_ITEM), steps);
    }

    @Test
    void anAveragePriceWaitsForMarketData() {
        var rule = new SystemOrderRule("iron", "IRON_INGOT");
        rule.price(new RulePrice(PriceMode.AVERAGE, 0, 0.8, 0, 0, 0));
        settings.rules(append(rule));

        assertEquals(Step.NO_PRICE, step(service.run()));
        averages.put("IRON_INGOT", 5.0);
        assertEquals(Step.PLACED, step(service.run()));
    }

    @Test
    void aSkippedRuleIsLoggedOnceUntilItsReasonChanges() {
        settings.mintedDailyCap(100);
        rule(fixed("iron", 2.0));

        service.run();
        service.run();
        settings.mintedDailyCap(0);
        settings.rules().getFirst().source(new MoneySource(SourceType.ACCOUNT, "", 0));
        service.run();

        assertEquals(2, logged.size());
        assertTrue(logged.get(0).contains("minted cap"));
        assertTrue(logged.get(1).contains("no account"));
    }

    @Test
    void aFailedPlacementGivesTheMoneyBack() {
        balances.put(BANK, 1000.0);
        failPlacement = true;
        rule(fixed("iron", 2.0)).source(new MoneySource(SourceType.ACCOUNT, BANK, 0));

        assertEquals(Step.FAILED, step(service.run()));

        assertEquals(1000.0, balances.get(BANK));
    }

    @Test
    void aPriceRaisedByAListenerIsPaidByTheAccount() {
        balances.put(BANK, 1000.0);
        changedPrice = 150.0;
        rule(fixed("iron", 2.0)).source(new MoneySource(SourceType.ACCOUNT, BANK, 0));

        assertEquals(Step.PLACED, step(service.run()));

        assertEquals(850.0, balances.get(BANK));
        assertEquals(150.0, onlyDecision().paid());
    }

    @Test
    void aPriceCutByAListenerGoesBackToTheAccount() {
        balances.put(BANK, 1000.0);
        changedPrice = 100.0;
        rule(fixed("iron", 2.0)).source(new MoneySource(SourceType.ACCOUNT, BANK, 0));

        service.run();

        assertEquals(900.0, balances.get(BANK));
        assertEquals(100.0, onlyDecision().paid());
    }

    @Test
    void aRaiseTheAccountCannotPayRemovesTheOrderAgain() {
        balances.put(BANK, 130.0);
        changedPrice = 150.0;
        rule(fixed("iron", 2.0)).source(new MoneySource(SourceType.ACCOUNT, BANK, 0));

        assertEquals(Step.CANNOT_PAY, step(service.run()));

        assertEquals(1, removed.size());
        assertEquals(130.0, balances.get(BANK));
    }

    @Test
    void aRaiseBeyondTheBudgetRemovesTheOrderAgain() {
        balances.put(BANK, 1000.0);
        changedPrice = 250.0;
        rule(fixed("iron", 2.0)).source(new MoneySource(SourceType.ACCOUNT, BANK, 200));

        assertEquals(Step.BUDGET, step(service.run()));

        assertEquals(1, removed.size());
        assertEquals(1000.0, balances.get(BANK));
    }

    @Test
    void aRaiseBeyondTheMintedCapRemovesTheOrderAgain() {
        settings.mintedDailyCap(200);
        changedPrice = 250.0;
        rule(fixed("iron", 2.0));

        assertEquals(Step.MINTED_CAP, step(service.run()));

        assertEquals(1, removed.size());
        assertTrue(balances.isEmpty());
    }

    @Test
    void aMintedOrderThatCannotBeRemovedAgainSaysSo() {
        failStore = true;
        failRemove = true;
        rule(fixed("iron", 2.0));

        assertEquals(Step.FAILED, step(service.run()));

        assertTrue(balances.isEmpty());
        assertTrue(logged.stream().anyMatch(message -> message.contains("stays open")));
    }

    @Test
    void aRaiseBeyondTheBudgetThatCannotBeRemovedKeepsTheMoneyAndSaysSo() {
        balances.put(BANK, 1000.0);
        changedPrice = 250.0;
        failRemove = true;
        rule(fixed("iron", 2.0)).source(new MoneySource(SourceType.ACCOUNT, BANK, 200));

        assertEquals(Step.BUDGET, step(service.run()));

        assertEquals(750.0, balances.get(BANK));
        assertTrue(logged.stream().anyMatch(message -> message.contains("is not given back")));
    }

    @Test
    void anOrderThatCannotBeRemovedAgainKeepsItsMoneyAndSaysSo() {
        balances.put(BANK, 1000.0);
        failStore = true;
        failRemove = true;
        rule(fixed("iron", 2.0)).source(new MoneySource(SourceType.ACCOUNT, BANK, 0));

        assertEquals(Step.FAILED, step(service.run()));

        assertEquals(872.0, balances.get(BANK));
        assertTrue(logged.stream().anyMatch(message -> message.contains("is not given back")));
    }

    @Test
    void anOrderWhoseDecisionIsNotStoredIsRemovedAgain() {
        balances.put(BANK, 1000.0);
        failStore = true;
        rule(fixed("iron", 2.0)).source(new MoneySource(SourceType.ACCOUNT, BANK, 0));

        assertEquals(Step.FAILED, step(service.run()));

        assertEquals(1, removed.size());
        assertEquals(1000.0, balances.get(BANK));
    }

    @Test
    void anExtendedOrderLivesLonger() {
        rule(fixed("iron", 2.0));
        service.run();
        var order = onlyDecision().order();
        placedAt(order, LocalDateTime.now(clock).plusHours(5));

        clock.advance(Duration.ofHours(12));
        assertEquals(List.of(), service.run().removed());
        clock.advance(Duration.ofHours(5));
        assertEquals(List.of(order), service.run().removed());
    }

    @Test
    void aDuplicateIdIsLoggedByItsPositionApartFromTheFirstRule() {
        settings.mintedDailyCap(100);
        rule(fixed("iron", 2.0));
        rule(fixed("iron", 3.0));

        var labels = service.run().rules().stream()
                .map(SystemOrderService.Result::rule)
                .toList();
        service.run();

        assertEquals(List.of("iron", "#2 (iron)"), labels);
        assertEquals(2, logged.size());
    }

    @Test
    void theGuessedIdIsReservedForGuessedOrders() {
        rule(fixed("guessed", 2.0));

        assertEquals(Step.INVALID_ID, step(service.run()));
        assertEquals(0, placements);
    }

    @Test
    void aDayStartsInTheServersZone() {
        Times.useZone(() -> ZoneId.of("Europe/Berlin"));
        try {
            clock.advance(Duration.ofMinutes(14 * 60 + 30));
            var rule = rule(fixed("iron", 2.0));
            rule.everyHours(1);
            rule.maxOpen(5);
            rule.lifetimeHours(48);
            rule.source(new MoneySource(SourceType.MINTED, "", 200));

            assertEquals(Step.PLACED, step(service.run()));
            clock.advance(Duration.ofHours(1));
            assertEquals(Step.PLACED, step(service.run()));
        } finally {
            Times.useZone(() -> ZoneOffset.UTC);
        }
    }

    private SystemDecision onlyDecision() {
        return system.orderData()
                .systemDecisions(OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .getFirst();
    }

    private static void placedAt(int order, LocalDateTime time) {
        query("UPDATE order_states SET last_update = ? WHERE id = ?")
                .single(call().bind(time, TimeColumns.SQLITE_TEXT).bind(order))
                .update();
    }

    private SystemOrderRule fixed(String id, double price) {
        var rule = new SystemOrderRule(id, "IRON_INGOT");
        rule.price(new RulePrice(PriceMode.FIXED, price, 1, 0, 0, 0));
        return rule;
    }

    private SystemOrderRule rule(SystemOrderRule rule) {
        settings.rules(append(rule));
        return rule;
    }

    private List<SystemOrderRule> append(SystemOrderRule rule) {
        var rules = new ArrayList<>(settings.rules());
        rules.add(rule);
        return rules;
    }

    private static Step step(SystemOrderService.Run run) {
        return run.rules().getFirst().step();
    }

    private final class FakePlacement implements SystemOrderService.Placement {
        @Override
        public boolean knows(String material) {
            return !material.equals("NOT_AN_ITEM");
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
            if (failPlacement) throw new IllegalStateException("placing failed");
            if (refusal != null) return CompletableFuture.completedFuture(new SystemOrderService.Refused(refusal));
            placements++;
            var order = system.place(0.0);
            system.orderData().systemRule(order.id(), wanted.rule());
            placedAt(order.id(), LocalDateTime.now(clock));
            var price = changedPrice != null ? changedPrice : OrderBuilder.total(wanted.unit(), wanted.amount());
            return CompletableFuture.completedFuture(new SystemOrderService.Created(order.id(), price));
        }

        @Override
        public boolean remove(int order) {
            if (failRemove) throw new IllegalStateException("removing failed");
            removed.add(order);
            SqLiteTestDatabase.setState(order, OrderState.RECEIVED.stateId(), 0);
            return true;
        }
    }

    private final class FakeFunding implements SystemOrderService.Funding {
        @Override
        public boolean withdraw(String account, double amount) {
            var balance = balances.getOrDefault(account, 0.0);
            if (balance < amount) return false;
            balances.put(account, balance - amount);
            return true;
        }

        @Override
        public void deposit(String account, double amount) {
            balances.merge(account, amount, Double::sum);
        }
    }
}
