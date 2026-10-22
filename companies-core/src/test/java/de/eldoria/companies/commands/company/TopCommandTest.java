/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.suggest.RankingProviders;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.ranking.TimeRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TopCommandTest {
    @Test
    void rangesAreTypedByTheirIds() {
        assertEquals(TimeRange.ALL, TopCommand.timeRange("all"));
        assertEquals(TimeRange.LAST_30_DAYS, TopCommand.timeRange("30D"));
        assertEquals(TimeRange.LAST_7_DAYS, TopCommand.timeRange("7d"));
    }

    @Test
    void anUnknownRangeFails() {
        assertThrows(CommandFailure.class, () -> TopCommand.timeRange("week"));
    }

    @Test
    void theRangeIdsAreSuggestedAllTimeFirst() {
        assertEquals(List.of("all", "30d", "7d"), RankingProviders.rangeIds());
    }
}
