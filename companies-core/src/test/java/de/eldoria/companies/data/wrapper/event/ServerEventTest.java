/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.event;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerEventTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Test
    void anEventAppliesFromItsStartUntilItsEndWhileActive() {
        var event = active(event(EventKind.TAX_REDUCTION, 5, List.of(), List.of(), null, null, null));

        assertFalse(event.activeAt(START.minusSeconds(1)));
        assertTrue(event.activeAt(START));
        assertTrue(event.activeAt(START.plusDays(1).minusSeconds(1)));
        assertFalse(event.activeAt(START.plusDays(1)));
        assertFalse(event(EventKind.TAX_REDUCTION, 5, List.of(), List.of(), null, null, null)
                .activeAt(START.plusHours(1)));
    }

    @Test
    void theScopeMatchesAnyMaterialOfTheOrderAndTheCompanyLevel() {
        var everything = event(EventKind.TAX_REDUCTION, 5, List.of(), List.of(), null, null, null);
        var wheat = event(EventKind.TAX_REDUCTION, 5, List.of("WHEAT"), List.of(), null, null, null);
        var levels = event(EventKind.TAX_REDUCTION, 5, List.of("WHEAT"), List.of(2, 3), null, null, null);

        assertTrue(everything.matches(List.of("STONE"), OptionalInt.empty()));
        assertTrue(wheat.matches(List.of("STONE", "WHEAT"), OptionalInt.of(1)));
        assertFalse(wheat.matches(List.of("STONE"), OptionalInt.of(1)));
        assertTrue(levels.matches(List.of("WHEAT"), OptionalInt.of(3)));
        assertFalse(levels.matches(List.of("WHEAT"), OptionalInt.of(1)));
        assertFalse(levels.matches(List.of("WHEAT"), OptionalInt.empty()));
        assertTrue(wheat.covers("WHEAT"));
        assertFalse(wheat.covers("STONE"));
        assertTrue(everything.covers("STONE"));
    }

    @Test
    void theRemainingBudgetNeverDropsBelowZero() {
        var none = event(EventKind.REWARD_MULTIPLIER, 2, List.of(), List.of(), SourceType.ACCOUNT, "Bank", null);
        var budget = event(EventKind.REWARD_MULTIPLIER, 2, List.of(), List.of(), SourceType.MINTED, null, 100.0);
        var overspent = new ServerEvent(
                1,
                "Overspent",
                EventKind.REWARD_MULTIPLIER,
                2,
                List.of(),
                List.of(),
                START,
                START.plusDays(1),
                SourceType.MINTED,
                null,
                100.0,
                120.0,
                EventState.ACTIVE,
                null,
                "Nora",
                START,
                null,
                null);

        assertEquals(OptionalDouble.empty(), none.remaining());
        assertEquals(OptionalDouble.of(100.0), budget.remaining());
        assertEquals(OptionalDouble.of(0.0), overspent.remaining());
    }

    @Test
    void anEventNeedsAFittingValueTimesScopeAndFunding() {
        assertEquals(Optional.empty(), problem(EventKind.TAX_REDUCTION, 5, List.of(), null, null, null));
        assertEquals(Optional.empty(), problem(EventKind.FEE_REDUCTION, 100, List.of(), null, null, null));
        assertEquals(
                Optional.empty(),
                problem(EventKind.REWARD_MULTIPLIER, 1.5, List.of(2), SourceType.ACCOUNT, "Bank", null));
        assertEquals(
                Optional.empty(), problem(EventKind.REWARD_MULTIPLIER, 2, List.of(), SourceType.MINTED, null, 50.0));

        assertEquals(
                Optional.of(ServerEvent.Problem.VALUE),
                problem(EventKind.TAX_REDUCTION, 0, List.of(), null, null, null));
        assertEquals(
                Optional.of(ServerEvent.Problem.VALUE),
                problem(EventKind.FEE_REDUCTION, 101, List.of(), null, null, null));
        assertEquals(
                Optional.of(ServerEvent.Problem.VALUE),
                problem(EventKind.REWARD_MULTIPLIER, 1, List.of(), SourceType.MINTED, null, 5.0));
        assertEquals(
                Optional.of(ServerEvent.Problem.MATERIALS),
                event(EventKind.TAX_REDUCTION, 5, List.of("WHEAT,CARROT"), List.of(), null, null, null)
                        .problem());
        assertEquals(
                Optional.of(ServerEvent.Problem.LEVEL_SCOPE),
                problem(EventKind.FEE_REDUCTION, 10, List.of(1), null, null, null));
        assertEquals(
                Optional.of(ServerEvent.Problem.LEVEL_SCOPE),
                problem(EventKind.TAX_REDUCTION, 10, List.of(0), null, null, null));
        assertEquals(
                Optional.of(ServerEvent.Problem.FUNDING),
                problem(EventKind.TAX_REDUCTION, 5, List.of(), SourceType.MINTED, null, 5.0));
        assertEquals(
                Optional.of(ServerEvent.Problem.FUNDING),
                problem(EventKind.REWARD_MULTIPLIER, 2, List.of(), null, null, 5.0));
        assertEquals(
                Optional.of(ServerEvent.Problem.ACCOUNT),
                problem(EventKind.REWARD_MULTIPLIER, 2, List.of(), SourceType.ACCOUNT, " ", null));
        assertEquals(
                Optional.of(ServerEvent.Problem.BUDGET),
                problem(EventKind.REWARD_MULTIPLIER, 2, List.of(), SourceType.MINTED, null, null));
        assertEquals(
                Optional.of(ServerEvent.Problem.BUDGET),
                problem(EventKind.REWARD_MULTIPLIER, 2, List.of(), SourceType.ACCOUNT, "Bank", 0.0));

        var backwards = ServerEvent.draft(
                "Backwards",
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                START,
                START,
                null,
                null,
                null,
                null,
                "Nora",
                START);
        assertEquals(Optional.of(ServerEvent.Problem.TIMES), backwards.problem());
        var unnamed = ServerEvent.draft(
                " ",
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                START,
                START.plusDays(1),
                null,
                null,
                null,
                null,
                "Nora",
                START);
        assertEquals(Optional.of(ServerEvent.Problem.NAME), unnamed.problem());
    }

    private static Optional<ServerEvent.Problem> problem(
            EventKind kind,
            double value,
            List<Integer> levels,
            @Nullable SourceType funding,
            @Nullable String account,
            @Nullable Double budget) {
        return event(kind, value, List.of(), levels, funding, account, budget).problem();
    }

    private static ServerEvent event(
            EventKind kind,
            double value,
            List<String> materials,
            List<Integer> levels,
            @Nullable SourceType funding,
            @Nullable String account,
            @Nullable Double budget) {
        return ServerEvent.draft(
                "Event",
                kind,
                value,
                materials,
                levels,
                START,
                START.plusDays(1),
                funding,
                account,
                budget,
                null,
                "Nora",
                START);
    }

    private static ServerEvent active(ServerEvent event) {
        return new ServerEvent(
                event.id(),
                event.name(),
                event.kind(),
                event.value(),
                event.materials(),
                event.levels(),
                event.starts(),
                event.ends(),
                event.funding(),
                event.account(),
                event.budget(),
                event.spent(),
                EventState.ACTIVE,
                event.template(),
                event.createdBy(),
                event.created(),
                null,
                null);
    }
}
