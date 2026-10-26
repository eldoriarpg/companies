/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.configuration.elements.systemorders.LearningSettings;
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
 *     <li><b>Outcomes</b>: an order is {@value #TAKEN} when a company or player accepted it, {@value #EXPIRED} when
 *     nobody did before it was repriced or removed, {@value #WITHDRAWN} when it made way for players.</li>
 *     <li><b>Price</b>: over a material's last {@code window} taken or expired orders, each one taken within
 *     {@code fastMinutes} lowers its factor by {@code step} (it paid too much), each expired one raises it (it paid
 *     too little), always between {@code minFactor} and the guessed {@code maxFactor}.</li>
 *     <li><b>Choice</b>: candidates are drawn at random, weighted by their low demand ({@code 1 / (1 + score)}) times
 *     their take rate ({@code (taken + 1) / (taken + expired + 2)}), which never falls below {@code weightFloor}, so
 *     every candidate is still tried now and then.</li>
 * </ul>
 */
public final class GuessedLearning {
    /**
     * A company or player accepted the order.
     */
    public static final String TAKEN = "TAKEN";

    /**
     * Nobody accepted the order before it was repriced or removed.
     */
    public static final String EXPIRED = "EXPIRED";

    /**
     * The order made way for players.
     */
    public static final String WITHDRAWN = "WITHDRAWN";

    /**
     * Width of the factor buckets the statistics are kept in.
     */
    public static final double BUCKET = 0.05;

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private GuessedLearning() {}

    /**
     * Reads the kept guessed orders and sums them up per material.
     *
     * @param orders   order repository
     * @param settings the learning settings
     * @return the statistics per material
     */
    public static Map<String, Stats> stats(AOrderData orders, LearningSettings settings) {
        var ended = new ArrayList<Ended>();
        for (var outcome : orders.systemOutcomes(GuessedSettings.RULE)) {
            if (!GuessedRecord.KIND.equals(outcome.decision().kind())) continue;
            GuessedRecord record;
            try {
                record = MAPPER.readValue(outcome.decision().record(), GuessedRecord.class);
            } catch (RuntimeException e) {
                continue;
            }
            ended.add(new Ended(
                    record.material(),
                    record.factor(),
                    outcome.decision().decidedAt(),
                    outcome.outcome(),
                    outcome.endedAt()));
        }
        return stats(ended, settings);
    }

    /**
     * Sums up the ended guessed orders per material.
     *
     * @param ended    the guessed orders, in any order
     * @param settings the learning settings
     * @return the statistics per material
     */
    public static Map<String, Stats> stats(List<Ended> ended, LearningSettings settings) {
        Map<String, List<Ended>> byMaterial = new HashMap<>();
        for (var order : ended)
            byMaterial
                    .computeIfAbsent(order.material(), key -> new ArrayList<>())
                    .add(order);
        Map<String, Stats> stats = new HashMap<>();
        byMaterial.forEach((material, orders) -> stats.put(material, stats(material, orders, settings)));
        return stats;
    }

    private static Stats stats(String material, List<Ended> orders, LearningSettings settings) {
        orders.sort(Comparator.comparing(Ended::placed));
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
        var concluded = orders.stream()
                .filter(order -> TAKEN.equals(order.outcome()) || EXPIRED.equals(order.outcome()))
                .sorted(Comparator.comparing(Ended::ended))
                .toList();
        var recent = concluded.subList(Math.max(0, concluded.size() - settings.window()), concluded.size());
        var offset = 0.0;
        for (var order : recent) {
            if (EXPIRED.equals(order.outcome())) offset += settings.step();
            else if (order.minutesToTake() < settings.fastMinutes()) offset -= settings.step();
        }
        var average = taken == 0 ? null : (double) minutes / taken;
        return new Stats(material, orders.size(), taken, expired, average, offset, Map.copyOf(buckets));
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
     * @param factor   the factor on the market average it paid
     * @param placed   when it was placed
     * @param outcome  how it ended, null while open or unnoticed
     * @param ended    when it ended, null without an outcome
     */
    public record Ended(
            String material,
            double factor,
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
     * @param material       the material
     * @param posted         orders placed
     * @param taken          orders taken
     * @param expired        orders nobody took before they were repriced or removed
     * @param minutesToTake  average minutes until an order was taken, null when none was
     * @param offset         how far learning moved the factor
     * @param buckets        per factor bucket, the orders placed and taken
     */
    public record Stats(
            String material,
            int posted,
            int taken,
            int expired,
            @Nullable Double minutesToTake,
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
         * @param base      the configured factor
         * @param minFactor the lowest factor
         * @param maxFactor the highest factor
         * @return the learned factor
         */
        public double factor(double base, double minFactor, double maxFactor) {
            return Math.clamp(base + offset, minFactor, Math.max(minFactor, maxFactor));
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
