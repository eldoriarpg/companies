/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.data.repository.AOrderData;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.DoubleSupplier;

/**
 * What guessed orders learn from how their earlier orders ended. Nothing is trained; everything is counts and bounded
 * steps, so an admin can read why an order was placed.
 * <ul>
 *     <li><b>Outcomes</b>: an order is {@value #TAKEN} when a company or player accepted it, {@value #REPRICED} when
 *     nobody did and a copy at a higher price replaced it, {@value #EXPIRED} when nobody took the last copy of such a
 *     chain, and {@value #WITHDRAWN} when it made way for players or guessed orders were switched off.</li>
 *     <li><b>Price</b>: a material's factor continues from the factor its latest order was placed with, as that
 *     order's record keeps it, or starts at the configured {@code factor} when no order of the last {@code days} has
 *     one. It then walks through the taken and expired orders that ended since, in the order they ended: each one
 *     taken within {@code fastMinutes} lowers it by {@code step} (it paid too much), each expired one raises it (it
 *     paid too little), slower takes keep it. It never leaves the range from {@code minFactor}, or {@code factor} when
 *     that is lower, to the guessed {@code maxFactor}. So it keeps what it learned for as long as the material is
 *     guessed; one not guessed for {@code days} starts over. An order an underbidding cap held down says nothing about
 *     the factor and does not move it.</li>
 *     <li><b>Choice</b>: candidates are drawn at random, weighted by their low demand ({@code 1 / (1 + score)}) times
 *     their take rate ({@code (taken + 1) / (taken + expired + 2)}, never below {@code weightFloor}), so every
 *     candidate is still tried now and then.</li>
 * </ul>
 */
public final class GuessedLearning {
    /**
     * A company or player accepted the order.
     */
    public static final String TAKEN = "TAKEN";

    /**
     * Nobody accepted the order, and a copy at a higher price replaced it.
     */
    public static final String REPRICED = "REPRICED";

    /**
     * Nobody accepted the last copy of the order before it was removed.
     */
    public static final String EXPIRED = "EXPIRED";

    /**
     * The order made way for players, or guessed orders were switched off.
     */
    public static final String WITHDRAWN = "WITHDRAWN";

    /**
     * Width of the factor buckets the statistics are kept in.
     */
    public static final double BUCKET = 0.05;

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private GuessedLearning() {}

    /**
     * Reads the guessed orders placed since a point in time and sums them up per material.
     *
     * @param orders   order repository
     * @param settings the guessed order settings
     * @param since    the oldest placement that counts
     * @return the statistics per material
     */
    public static Map<String, Stats> stats(AOrderData orders, GuessedSettings settings, LocalDateTime since) {
        var ended = new ArrayList<Ended>();
        for (var outcome : orders.systemOutcomes(GuessedSettings.RULE, since)) {
            if (!GuessedRecord.KIND.equals(outcome.decision().kind())) continue;
            GuessedRecord record;
            try {
                record = MAPPER.readValue(outcome.decision().record(), GuessedRecord.class);
            } catch (RuntimeException e) {
                continue;
            }
            var paid = record.average() > 0 ? record.unit() / record.average() : record.factor();
            var learned = record.learning();
            ended.add(new Ended(
                    record.material(),
                    paid,
                    GuessedOrders.capped(record),
                    learned == null ? null : learned.base(),
                    outcome.decision().decidedAt(),
                    outcome.outcome(),
                    outcome.endedAt()));
        }
        return stats(ended, settings);
    }

    /**
     * Sums up the guessed orders per material.
     *
     * @param ended    the guessed orders, in any order
     * @param settings the guessed order settings
     * @return the statistics per material
     */
    public static Map<String, Stats> stats(List<Ended> ended, GuessedSettings settings) {
        Map<String, List<Ended>> byMaterial = new HashMap<>();
        for (var order : ended)
            byMaterial
                    .computeIfAbsent(order.material(), key -> new ArrayList<>())
                    .add(order);
        Map<String, Stats> stats = new HashMap<>();
        byMaterial.forEach((material, orders) -> stats.put(material, stats(material, orders, settings)));
        return stats;
    }

    private static Stats stats(String material, List<Ended> orders, GuessedSettings settings) {
        var learning = settings.learning();
        int taken = 0;
        int expired = 0;
        long minutes = 0;
        Map<Integer, int[]> buckets = new TreeMap<>();
        for (var order : orders) {
            var bucket = buckets.computeIfAbsent(bucket(order.factor()), key -> new int[2]);
            bucket[0]++;
            if (TAKEN.equals(order.outcome())) {
                taken++;
                bucket[1]++;
                minutes += order.minutesToTake();
            } else if (EXPIRED.equals(order.outcome())) {
                expired++;
            }
        }
        var lower = lowest(settings);
        var factor = settings.factor();
        var since = LocalDateTime.MIN;
        var latest = orders.stream().filter(order -> order.base() != null).max(Comparator.comparing(Ended::placed));
        if (latest.isPresent()) {
            factor = Math.clamp(latest.get().base(), lower, settings.maxFactor());
            since = latest.get().placed();
        }
        var from = since;
        var concluded = orders.stream()
                .filter(order -> order.ended() != null && order.ended().isAfter(from))
                .sorted(Comparator.comparing(Ended::ended))
                .toList();
        for (var order : concluded) {
            if (EXPIRED.equals(order.outcome()) && !order.capped()) {
                factor = Math.min(settings.maxFactor(), factor + learning.step());
            } else if (TAKEN.equals(order.outcome()) && order.minutesToTake() < learning.fastMinutes()) {
                factor = Math.max(lower, factor - learning.step());
            }
        }
        var average = taken == 0 ? null : (double) minutes / taken;
        return new Stats(
                material,
                orders.size(),
                taken,
                expired,
                average,
                factor,
                factor - settings.factor(),
                Map.copyOf(buckets));
    }

    /**
     * The lowest factor learning reaches.
     *
     * @param settings the guessed order settings
     * @return {@code minFactor}, or {@code factor} when that is lower
     */
    public static double lowest(GuessedSettings settings) {
        return Math.min(settings.learning().minFactor(), settings.factor());
    }

    /**
     * The bucket of a factor.
     *
     * @param factor a factor on the market average
     * @return the factor in steps of {@value #BUCKET}, rounded
     */
    public static int bucket(double factor) {
        return (int) Math.round(factor / BUCKET);
    }

    /**
     * Draws one candidate, weighted.
     *
     * @param weights the weight of each candidate, all positive
     * @param random  a random number from 0 included to 1 excluded
     * @return the index drawn, -1 for no candidates
     */
    public static int draw(List<Double> weights, DoubleSupplier random) {
        var total = weights.stream().mapToDouble(Double::doubleValue).sum();
        if (weights.isEmpty() || !(total > 0)) return -1;
        var target = random.getAsDouble() * total;
        for (var index = 0; index < weights.size(); index++) {
            target -= weights.get(index);
            if (target < 0) return index;
        }
        return weights.size() - 1;
    }

    /**
     * A guessed order as learning sees it.
     *
     * @param material the material
     * @param factor   the factor on the market average it paid, after any cap
     * @param capped   whether an underbidding cap held its price down
     * @param base     the factor learning had reached when it was placed, null without learning
     * @param placed   when it was placed
     * @param outcome  how it ended, null while open or unnoticed
     * @param ended    when it ended, null without an outcome
     */
    public record Ended(
            String material,
            double factor,
            boolean capped,
            @Nullable Double base,
            LocalDateTime placed,
            @Nullable String outcome,
            @Nullable LocalDateTime ended) {
        /**
         * @return minutes from placement to its end, 0 without an end
         */
        public long minutesToTake() {
            return ended == null
                    ? 0
                    : Math.max(0, Duration.between(placed, ended).toMinutes());
        }
    }

    /**
     * What the guessed orders of one material did.
     *
     * @param material      the material
     * @param posted        orders placed
     * @param taken         orders taken
     * @param expired       chains of orders nobody took
     * @param minutesToTake average minutes until an order was taken, null when none was
     * @param factor        the factor learning walked to
     * @param offset        how far that is from the configured factor
     * @param buckets       per bucket of the factor paid, the orders placed and taken
     */
    public record Stats(
            String material,
            int posted,
            int taken,
            int expired,
            @Nullable Double minutesToTake,
            double factor,
            double offset,
            Map<Integer, int[]> buckets) {
        /**
         * @param floor the lowest rate
         * @return the share of orders taken, smoothed so a new material starts at one half, at least {@code floor}
         */
        public double takeRate(double floor) {
            return Math.max(floor, (taken + 1.0) / (taken + expired + 2.0));
        }

        /**
         * @param factor a factor
         * @return orders placed and taken at about that factor
         */
        public int[] bucket(double factor) {
            var counts = buckets.get(GuessedLearning.bucket(factor));
            return counts == null ? new int[2] : counts.clone();
        }
    }
}
