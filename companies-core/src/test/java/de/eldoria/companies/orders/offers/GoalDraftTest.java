/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.configuration.elements.systemorders.RuleEntry;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoalDraftTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);
    private static final OfferLine LOGS = OfferLine.material("OAK_LOG");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aGoalIsPaidForInFullAndStartsWhenAsked() {
        var goal = GoalDraft.goal(" Harbour logs ", "Minted", LOGS, 1000, 1.25, 256, 2, 48, null, "Admin", NOW);

        assertEquals("Harbour logs", goal.name());
        assertEquals(SourceType.MINTED, goal.source());
        assertNull(goal.account());
        assertEquals(1250.0, goal.budget());
        assertEquals(256, goal.chunkSize());
        assertEquals(GoalDraft.OPEN_CHUNKS, goal.openChunks());
        assertEquals(NOW.plusHours(2), goal.starts());
        assertEquals(NOW.plusHours(50), goal.ends());
        assertEquals(OfferState.SCHEDULED, goal.state());
        assertEquals("Admin", goal.createdBy());
    }

    @Test
    void anAccountPaysAndAChunkIsNoLargerThanTheGoal() {
        var goal = GoalDraft.goal("Logs", "orders/Collector", LOGS, 50, 2, 256, 0, 1, null, "Admin", NOW);

        assertEquals(SourceType.ACCOUNT, goal.source());
        assertEquals("orders/Collector", goal.account());
        assertEquals(50, goal.chunkSize());
        assertEquals(NOW, goal.starts());
    }

    @Test
    void theBudgetIsTheSumOfTheRoundedChunks() {
        assertEquals(1.02, GoalDraft.budget(0.335, 3, 1));
        assertEquals(1.01, GoalDraft.budget(0.335, 3, 3));
        assertEquals(1250.0, GoalDraft.budget(1.25, 1000, 256));
    }

    @Test
    void unusableValuesAreRefused() {
        assertEquals("companyadmin.offers.error.name", refusal(" ", "minted", 10, 1, 10, 0, 1));
        assertEquals("companyadmin.offers.error.name", refusal("x".repeat(32), "minted", 10, 1, 10, 0, 1));
        assertEquals("companyadmin.offers.error.numbers", refusal("Logs", "minted", 0, 1, 10, 0, 1));
        assertEquals("companyadmin.offers.error.numbers", refusal("Logs", "minted", 10, 1, 10, -1, 1));
        assertEquals("companyadmin.offers.error.price", refusal("Logs", "minted", 10, 0, 10, 0, 1));
        assertEquals("companyadmin.offers.error.money", refusal("Logs", " ", 10, 1, 10, 0, 1));
        assertEquals("companyadmin.offers.error.hours", refusal("Logs", "minted", 10, 1, 10, 0, 8761));
        assertEquals("companyadmin.offers.error.hours", refusal("Logs", "minted", 10, 1, 10, 8761, 1));
        assertEquals("companyadmin.offers.error.price", refusal("Logs", "minted", 10, 1_000_001, 10, 0, 1));
        assertEquals("companyadmin.offers.error.price", refusal("Logs", "minted", 10, Double.NaN, 10, 0, 1));
        assertEquals(
                "companyadmin.offers.error.budgetHigh",
                refusal("Logs", "minted", 10, 1, 10, 0, 1, Double.POSITIVE_INFINITY));
        assertEquals("companyadmin.offers.error.budgetHigh", refusal("Logs", "minted", 10, 1, 10, 0, 1, 2e12));
        assertEquals("companyadmin.offers.error.budgetLow", refusal("Logs", "minted", 10, 1, 10, 0, 1, 0.99));
        assertEquals(
                "companyadmin.offers.error.budgetHigh",
                refusal("Logs", "minted", Integer.MAX_VALUE, 1_000_000, 10, 0, 1));
    }

    @Test
    void aBudgetOfItsOwnReplacesTheCost() {
        var goal = GoalDraft.goal("Logs", "minted", LOGS, 1000, 1, 100, 0, 8760, 250.0, "Admin", NOW);

        assertEquals(250.0, goal.budget());
        assertEquals(NOW.plusHours(8760), goal.ends());
    }

    @Test
    void linesNameKnownItemsOrReadableCriteria() {
        assertEquals(
                "OAK_LOG",
                GoalDraft.material("oak_log", name -> Optional.of("OAK_LOG")).material());
        assertEquals(
                "companyadmin.offers.error.material",
                assertThrows(CommandFailure.class, () -> GoalDraft.material("cloud", name -> Optional.empty()))
                        .localeKey());
        var criteria = GoalDraft.criteria(" oak_log birch_log ");
        assertEquals("oak_log birch_log", criteria.criteria());
        assertEquals(RuleEntry.Resolve.ANY, criteria.resolve());
        assertThrows(CommandFailure.class, () -> GoalDraft.criteria("#no_such_tag"));
    }

    private static String refusal(
            String name, String money, int amount, double price, int chunk, int start, int hours) {
        return refusal(name, money, amount, price, chunk, start, hours, null);
    }

    private static String refusal(
            String name, String money, int amount, double price, int chunk, int start, int hours, Double budget) {
        return assertThrows(
                        CommandFailure.class,
                        () -> GoalDraft.goal(
                                name, money, LOGS, amount, price, chunk, start, hours, budget, "Admin", NOW))
                .localeKey();
    }
}
