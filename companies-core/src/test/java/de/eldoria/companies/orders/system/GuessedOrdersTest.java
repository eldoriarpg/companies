/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings.Competition;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
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
import de.eldoria.companies.data.wrapper.order.SystemOutcome;
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
    private String failing;
    private boolean failOutcomes;
    private boolean failOpenPlayers;
    private boolean failRest;
    private double balance = Double.MAX_VALUE;
    private final SystemOrderService.Funding funding = new SystemOrderService.Funding() {
        @Override
        public boolean withdraw(String account, double amount) {
            if (balance < amount) return false;
            balance -= amount;
            return true;
        }

        @Override
        public void deposit(String account, double amount) {}
    };

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        Times.useZone(() -> ZoneOffset.UTC);
        SqLiteTestDatabase.create(directory);
        orderData = new SqLiteOrderData(null, SqLiteTestDatabase.mapper()) {
            @Override
            public void restMaterial(String material, LocalDateTime until) {
                if (failRest) throw new IllegalStateException("writing failed");
                super.restMaterial(material, until);
            }

            @Override
            public int openPlayerOrders() {
                if (failOpenPlayers) throw new IllegalStateException("reading failed");
                return super.openPlayerOrders();
            }

            @Override
            public List<SystemOutcome> systemOutcomes(String rule, LocalDateTime since) {
                if (failOutcomes) throw new IllegalStateException("reading failed");
                return super.systemOutcomes(rule, since);
            }
        };
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
                () -> settings, orderData, new FakePlacement(), funding, clock, () -> 0.5, logger);
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
        var defined = order(system, Material.SAND, 1, 1.0);
        orderData.systemRule(defined.id(), "iron");
        assertTrue(limit.refusal(company, defined).isEmpty());
        settings.guessed().perCompanyDaily(0);
        assertTrue(limit.refusal(company, guessed).isEmpty());
    }

    @Test
    void aFailingPlacementDoesNotStopTheOthers() {
        settings.guessed().minOpen(2);
        settings.guessed().maxOpen(2);
        failing = "DIRT";

        var steps = service.run().rules();

        assertEquals(
                List.of(
                        new SystemOrderService.Result("guessed DIRT", Step.FAILED),
                        new SystemOrderService.Result("guessed STONE", Step.PLACED)),
                steps);
    }

    @Test
    void anOrderHeldDownByTheUnderbidCapRestsInsteadOfRepricing() {
        settings.guessed().competition(Competition.UNDERBID);
        settings.guessed().repriceAfterHours(2);
        service.run();
        assertEquals(1.4, only().unit());

        clock.advance(Duration.ofHours(2));
        service.run();

        assertTrue(orderData.restingMaterials(now()).contains("DIRT"));
        assertEquals("STONE", only().material());
    }

    @Test
    void anUnderbidCapBelowTheLowestPriceGuessesNothing() {
        settings.guessed().competition(Competition.UNDERBID);
        settings.guessed().minPrice(1.5);

        var steps = service.run().rules().stream()
                .map(SystemOrderService.Result::step)
                .toList();

        assertEquals(List.of(Step.NO_PRICE, Step.NO_PRICE), steps);
        assertTrue(guessedDecisions().isEmpty());
    }

    @Test
    void allowAndDenyEntriesAreResolvedToMaterialNames() {
        settings.guessed().minOrders(5);
        settings.guessed().allow(List.of("iron ingot"));
        averages.put("IRON_INGOT", 3.0);

        service.run();

        assertEquals("IRON_INGOT", only().material());
    }

    @Test
    void aDeniedMaterialIsNeverGuessed() {
        settings.guessed().deny(List.of("dirt"));

        service.run();

        assertEquals("STONE", only().material());
    }

    @Test
    void anOrderRemovedForRepricingGivesItsBudgetBack() {
        settings.guessed().repriceAfterHours(2);
        settings.guessed().repriceStep(0);
        settings.guessed().source(new MoneySource(MoneySource.SourceType.MINTED, "", 120));
        service.run();

        clock.advance(Duration.ofHours(2));
        service.run();

        assertEquals(1, only().step());
    }

    @Test
    void aCheaperOrderIsStillTriedWhenMoneyStopsOne() {
        settings.guessed().minOpen(2);
        settings.guessed().maxOpen(2);
        settings.mintedDailyCap(80);

        var steps = service.run().rules();

        assertEquals(
                List.of(
                        new SystemOrderService.Result("guessed DIRT", Step.MINTED_CAP),
                        new SystemOrderService.Result("guessed STONE", Step.PLACED)),
                steps);
    }

    @Test
    void anAccountThatCannotPayLetsTheNextCandidateTry() {
        settings.guessed().minOpen(2);
        settings.guessed().maxOpen(2);
        settings.guessed().source(new MoneySource(MoneySource.SourceType.ACCOUNT, "bank", 0));
        balance = 60.0;

        var steps = service.run().rules();

        assertEquals(
                List.of(
                        new SystemOrderService.Result("guessed DIRT", Step.CANNOT_PAY),
                        new SystemOrderService.Result("guessed STONE", Step.PLACED)),
                steps);
    }

    @Test
    void moneyStoppingThreePlacementsEndsTheCheck() {
        settings.guessed().minOpen(5);
        settings.guessed().maxOpen(5);
        settings.guessed().allow(List.of("SAND", "GRAVEL", "CLAY_BALL"));
        for (var material : List.of("SAND", "GRAVEL", "CLAY_BALL")) averages.put(material, 1.0);
        settings.mintedDailyCap(1);

        var steps = service.run().rules();

        assertEquals(GuessedOrders.MONEY_FAILURES, steps.size());
        assertTrue(steps.stream().allMatch(result -> result.step() == Step.MINTED_CAP));
    }

    @Test
    void withoutAnAccountTheCheckStopsPlacing() {
        settings.guessed().minOpen(2);
        settings.guessed().maxOpen(2);
        settings.guessed().source(new MoneySource(MoneySource.SourceType.ACCOUNT, "", 0));

        var steps = service.run().rules();

        assertEquals(List.of(new SystemOrderService.Result("guessed DIRT", Step.NO_ACCOUNT)), steps);
    }

    @Test
    void aReplacementStoppedByMoneyDoesNotRest() {
        settings.guessed().repriceAfterHours(2);
        settings.guessed().minOpen(2);
        settings.guessed().maxOpen(2);
        service.run();
        settings.mintedDailyCap(0);

        clock.advance(Duration.ofHours(2));
        service.run();

        assertTrue(guessedDecisions().isEmpty());
        assertTrue(orderData.restingMaterials(now()).isEmpty());
    }

    @Test
    void aReplacementWithoutAFreeSlotLetsItsMaterialRest() {
        settings.guessed().repriceAfterHours(2);
        service.run();
        var first = onlyDecision().order();
        settings.guessed().minOpen(0);

        clock.advance(Duration.ofHours(2));
        service.run();

        assertTrue(guessedDecisions().isEmpty());
        assertTrue(orderData.restingMaterials(now()).contains("DIRT"));
        assertEquals(GuessedLearning.EXPIRED, outcome(first));
    }

    @Test
    void aDecisionOfAnotherKindUnderTheGuessedRuleIsLeftAlone() {
        settings.guessed().repriceAfterHours(2);
        var foreign = order(system, Material.SAND, 1, 1.0);
        orderData.systemRule(foreign.id(), GuessedSettings.RULE);
        at(foreign.id(), now());
        orderData.saveSystemDecision(
                new SystemDecision(foreign.id(), GuessedSettings.RULE, "DEFINED", now(), null, 1, "{}"));

        clock.advance(Duration.ofHours(2));
        var run = service.run();

        assertTrue(orderData.retrieveOrderById(foreign.id()).join().isPresent());
        assertTrue(!run.removed().contains(foreign.id()));
    }

    @Test
    void howAnOrderEndedIsNotedForLearning() {
        settings.guessed().repriceAfterHours(2);
        settings.guessed().maxReprices(1);
        service.run();
        var repriced = onlyDecision().order();
        clock.advance(Duration.ofHours(2));
        service.run();
        var expired = onlyDecision().order();
        clock.advance(Duration.ofHours(2));
        service.run();
        var withdrawn = onlyDecision().order();

        open(Material.STONE, 10, 30.0);
        service.run();

        assertEquals(GuessedLearning.REPRICED, outcome(repriced));
        assertEquals(GuessedLearning.EXPIRED, outcome(expired));
        assertEquals(GuessedLearning.WITHDRAWN, outcome(withdrawn));
    }

    @Test
    void ordersRunningOutWhileDisabledAreNotedAsWithdrawn() {
        settings.guessed().repriceAfterHours(2);
        service.run();
        var order = onlyDecision().order();
        settings.guessed().enabled(false);

        clock.advance(Duration.ofHours(2));
        service.run();

        assertEquals(GuessedLearning.WITHDRAWN, outcome(order));
    }

    @Test
    void aTakeGivenUpAgainIsReplacedByHowTheOrderEnded() {
        settings.guessed().repriceAfterHours(2);
        settings.guessed().maxReprices(0);
        service.run();
        var order = onlyDecision().order();
        orderData.systemOutcome(order, GuessedLearning.TAKEN, now().plusMinutes(5), null);

        clock.advance(Duration.ofHours(2));
        service.run();

        assertEquals(GuessedLearning.EXPIRED, outcome(order));
    }

    @Test
    void theWeightedDrawCanPickALowerRankedMaterial() {
        var drawing = new SystemOrderService(
                () -> settings,
                orderData,
                new FakePlacement(),
                funding,
                clock,
                () -> 0.99,
                Logger.getAnonymousLogger());

        drawing.run();

        assertEquals("STONE", only().material());
        assertEquals(2, only().rank());
    }

    @Test
    void aReplacementMoneyStoppedIsNotedExpired() {
        settings.guessed().repriceAfterHours(2);
        service.run();
        var first = onlyDecision().order();
        settings.mintedDailyCap(0);

        clock.advance(Duration.ofHours(2));
        service.run();

        assertEquals(GuessedLearning.EXPIRED, outcome(first));
    }

    @Test
    void replacementsLeftWhenTheCheckStopsEarlyAreNotedExpired() {
        settings.guessed().repriceAfterHours(2);
        settings.guessed().minOpen(2);
        settings.guessed().maxOpen(2);
        service.run();
        var first = guessedDecisions().get(0).order();
        var second = guessedDecisions().get(1).order();
        settings.guessed().source(new MoneySource(MoneySource.SourceType.ACCOUNT, "", 0));

        clock.advance(Duration.ofHours(2));
        service.run();

        assertEquals(GuessedLearning.EXPIRED, outcome(first));
        assertEquals(GuessedLearning.EXPIRED, outcome(second));
    }

    @Test
    void aRemovedOrderIsNotedExpiredWhenTheCheckFailsBeforeItsCopy() {
        settings.guessed().repriceAfterHours(2);
        service.run();
        var first = onlyDecision().order();
        failOpenPlayers = true;

        clock.advance(Duration.ofHours(2));
        service.run();

        assertEquals(GuessedLearning.EXPIRED, outcome(first));
    }

    @Test
    void aQueuedRepriceIsNotedExpiredWhenRemovingALaterOrderFails() {
        settings.guessed().repriceAfterHours(2);
        settings.guessed().minOpen(2);
        settings.guessed().maxOpen(2);
        service.run();
        var dirt = guessedDecisions().stream()
                .filter(decision -> decision.record().contains("\"DIRT\""))
                .findFirst()
                .orElseThrow()
                .order();
        settings.guessed().deny(List.of("STONE"));
        failRest = true;

        clock.advance(Duration.ofHours(2));
        service.run();

        assertEquals(GuessedLearning.EXPIRED, outcome(dirt));
    }

    @Test
    void aRepriceIsNotedOnlyOnceItsCopyIsPlaced() {
        settings.guessed().repriceAfterHours(2);
        service.run();
        var first = onlyDecision().order();

        clock.advance(Duration.ofHours(2));
        service.run();

        assertEquals(GuessedLearning.REPRICED, outcome(first));
        assertEquals(first, only().replaces());
    }

    @Test
    void whenLearningCannotBeReadOrdersFollowTheRank() {
        failOutcomes = true;
        var drawing = new SystemOrderService(
                () -> settings,
                orderData,
                new FakePlacement(),
                funding,
                clock,
                () -> 0.99,
                Logger.getAnonymousLogger());

        drawing.run();

        assertEquals("DIRT", only().material());
        assertNull(only().learning());
    }

    @Test
    void aRecordWrittenBeforeLearningIsStillRead() {
        service.run();
        var stored = (tools.jackson.databind.node.ObjectNode)
                JsonMapper.builder().build().readTree(onlyDecision().record());
        stored.remove("learning");

        var record = JsonMapper.builder().build().treeToValue(stored, GuessedRecord.class);

        assertEquals("DIRT", record.material());
        assertNull(record.learning());
    }

    @Test
    void aMaterialTakenFastIsPricedLowerNextTime() {
        service.run();
        var first = onlyDecision().order();
        orderData.systemOutcome(first, GuessedLearning.TAKEN, now().plusMinutes(5), null);
        removeOrder(first);

        clock.advance(Duration.ofMinutes(10));
        service.run();

        var record = only();
        assertEquals("DIRT", record.material());
        assertEquals(0.75, record.factor(), 0.000_1);
        assertEquals(1.5, record.unit());
        var learning = record.learning();
        assertEquals(-0.05, learning.offset(), 0.000_1);
        assertEquals(1, learning.taken());
        assertEquals(5.0, learning.minutesToTake());
    }

    @Test
    void withoutLearningOrdersFollowTheRankAndRecordNoLearning() {
        settings.guessed().learning().enabled(false);

        service.run();

        assertEquals("DIRT", only().material());
        assertNull(only().learning());
    }

    @Test
    void aNewOrderRecordsTheWeightItWasDrawnWith() {
        service.run();

        var learning = only().learning();
        assertEquals(0.5, learning.weight(), 0.000_1);
        assertEquals(0.5, learning.takeRate(), 0.000_1);
    }

    private String outcome(int order) {
        return orderData.systemOutcomes(GuessedSettings.RULE, now().minusDays(30)).stream()
                .filter(entry -> entry.decision().order() == order)
                .findFirst()
                .orElseThrow()
                .outcome();
    }

    private static void removeOrder(int order) {
        for (var table : List.of("order_content", "order_states", "orders")) {
            query("DELETE FROM " + table + " WHERE id = ?")
                    .single(call().bind(order))
                    .delete();
        }
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
        public String canonical(String material) {
            var found = Material.matchMaterial(material);
            return found == null ? null : found.name();
        }

        @Override
        public int stackSize(String material) {
            return 64;
        }

        @Override
        public CompletableFuture<SystemOrderService.Placed> place(SystemOrderService.Wanted wanted) {
            if (wanted.material().equals(failing)) throw new IllegalStateException("placing failed");
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
            for (var table : List.of("order_content", "order_states", "orders")) {
                query("DELETE FROM " + table + " WHERE id = ?")
                        .single(call().bind(order))
                        .delete();
            }
            return true;
        }
    }
}
