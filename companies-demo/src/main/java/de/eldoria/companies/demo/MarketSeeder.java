/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes a price history for the materials of the seed, so the market of the dashboard has something to show after
 * a reset: one row per material and day from {@value #FIRST_DAY_BACK} days ago back to the configured number of days,
 * with at least three orders a day so every day counts. The days since then are left to the plugin's own recording,
 * which rewrites them from the orders delivered meanwhile; the history says it is recorded through yesterday so the
 * recording starts there. The history is written only into an empty market, so a restart does not write it twice, and
 * its state comes first, so the plugin's own recording, should it run meanwhile, never sweeps over the written days.
 */
final class MarketSeeder {
    /**
     * The newest day written, in days before today.
     */
    static final int FIRST_DAY_BACK = 3;

    private final Store store;
    private final Clock clock;

    /**
     * @param store where the history goes
     * @param clock the time, in UTC
     */
    MarketSeeder(Store store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /**
     * Writes the history.
     *
     * @param prices the base price of each material, by material name
     * @param days   how many days back the history reaches
     * @return the rows written, 0 when the market has a history already
     */
    int seed(Map<String, Double> prices, int days) {
        if (prices.isEmpty() || store.hasHistory()) return 0;
        var today = LocalDate.now(clock);
        List<Row> rows = new ArrayList<>();
        var index = 0;
        for (var price : prices.entrySet()) {
            index++;
            for (var back = FIRST_DAY_BACK; back <= days; back++) {
                rows.add(row(today.minusDays(back), price.getKey(), price.getValue(), back, index));
            }
        }
        if (rows.isEmpty()) return 0;
        store.recordedThrough(today.minusDays(1));
        store.days(rows);
        store.refreshPrices();
        return rows.size();
    }

    private static Row row(LocalDate day, String material, double base, int back, int index) {
        var orders = 3 + Math.floorMod(back * 7 + index * 3, 6);
        var average = round(base * (1 + 0.15 * Math.sin(back / 4.0 + index) - 0.004 * back));
        var items = (long) orders * (32 + Math.floorMod(day.toEpochDay() * 13, 5) * 32);
        return new Row(day, material, orders, items, average, round(average * 0.85), round(average * 1.2));
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /**
     * The history of one material on one day.
     *
     * @param day      the day
     * @param material the material name
     * @param orders   how many orders delivered it
     * @param items    how many items they delivered
     * @param average  the average unit price
     * @param lowest   the lowest unit price
     * @param highest  the highest unit price
     */
    record Row(LocalDate day, String material, int orders, long items, double average, double lowest, double highest) {}

    /**
     * Where the history goes: the market tables of the Companies database.
     */
    interface Store {
        /**
         * @return whether the market has any recorded day
         */
        boolean hasHistory();

        /**
         * @param rows the rows to write, replacing rows of the same day and material
         */
        void days(List<Row> rows);

        /**
         * @param day the last day the history counts as recorded
         */
        void recordedThrough(LocalDate day);

        /**
         * Sets the average, lowest and highest price of every material from the history.
         */
        void refreshPrices();
    }
}
