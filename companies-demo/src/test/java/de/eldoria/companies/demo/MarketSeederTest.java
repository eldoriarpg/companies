/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSeederTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

    private final List<MarketSeeder.Row> rows = new ArrayList<>();
    private final List<LocalDate> recorded = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();
    private int refreshed;
    private boolean history;

    private final MarketSeeder seeder = new MarketSeeder(
            new MarketSeeder.Store() {
                @Override
                public boolean hasHistory() {
                    return history;
                }

                @Override
                public void days(List<MarketSeeder.Row> written) {
                    calls.add("days");
                    rows.addAll(written);
                }

                @Override
                public void recordedThrough(LocalDate day) {
                    calls.add("state");
                    recorded.add(day);
                }

                @Override
                public void refreshPrices() {
                    calls.add("prices");
                    refreshed++;
                }
            },
            Clock.fixed(Instant.parse("2026-10-31T23:30:00Z"), ZoneOffset.UTC));

    @Test
    void writesEveryDayFromThreeDaysAgoWithEnoughOrdersAndPricesAroundTheBase() {
        var prices = new LinkedHashMap<String, Double>();
        prices.put("STONE", 0.4);
        prices.put("DIAMOND", 25.0);

        assertEquals(56, seeder.seed(prices, 30));

        assertEquals(TODAY.minusDays(3), rows.getFirst().day());
        assertEquals(TODAY.minusDays(30), rows.get(27).day());
        for (var row : rows) {
            assertTrue(row.orders() >= 3, row::toString);
            assertTrue(row.lowest() <= row.average() && row.average() <= row.highest(), row::toString);
            var base = row.material().equals("STONE") ? 0.4 : 25.0;
            assertTrue(row.average() > base * 0.7 && row.average() < base * 1.2, row::toString);
        }
        assertEquals(List.of(TODAY.minusDays(1)), recorded);
        assertEquals(List.of("state", "days", "prices"), calls);
    }

    @Test
    void aMarketWithAHistoryIsLeftAlone() {
        history = true;

        assertEquals(0, seeder.seed(new LinkedHashMap<>(Map.of("STONE", 0.4)), 30));

        assertTrue(calls.isEmpty());
    }

    @Test
    void writesNothingWithoutMaterials() {
        assertEquals(0, seeder.seed(new LinkedHashMap<>(), 30));

        assertTrue(recorded.isEmpty());
        assertEquals(0, refreshed);
    }
}
