/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.dashboard.MarketRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketMergeTest {
    private static final MarketRow STONE_LIVE = new MarketRow("STONE", 1, 64, 0.5, 0.5, 0.5);
    private static final MarketRow STONE_HISTORY = new MarketRow("STONE", 120, 9000, 0.4, 0.3, 0.6);
    private static final MarketRow DIRT_LIVE = new MarketRow("DIRT", 4, 256, 0.1, 0.1, 0.1);
    private static final MarketRow WOOL_HISTORY = new MarketRow("WHITE_WOOL", 50, 3000, 0.9, 0.7, 1.1);

    @Test
    void aFewDeliveriesDoNotHideARecordedPrice() {
        assertEquals(
                List.of(STONE_HISTORY, WOOL_HISTORY, DIRT_LIVE),
                ADashboardData.merged(List.of(STONE_LIVE, DIRT_LIVE), List.of(STONE_HISTORY, WOOL_HISTORY), 3));
    }

    @Test
    void enoughDeliveriesShowAtOnce() {
        var stone = new MarketRow("STONE", 3, 192, 0.5, 0.5, 0.5);

        assertEquals(List.of(stone), ADashboardData.merged(List.of(stone), List.of(STONE_HISTORY), 3));
    }

    @Test
    void aThinHistoryLeavesTheLiveRow() {
        var thin = new MarketRow("STONE", 2, 128, 0.4, 0.4, 0.4);

        assertEquals(List.of(STONE_LIVE), ADashboardData.merged(List.of(STONE_LIVE), List.of(thin), 3));
    }
}
