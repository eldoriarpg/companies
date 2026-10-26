/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.configuration.elements.systemorders.RulePrice;
import de.eldoria.companies.data.wrapper.order.DeliveredVolume;
import de.eldoria.companies.data.wrapper.order.PlayerDemand;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.DoubleSupplier;

/**
 * Decides what the server guesses: which materials, at what price, how many items and how many orders.
 * <ul>
 *     <li><b>Candidates</b> are the materials with at least {@code minOrders} delivered player orders in the last
 *     {@code historyDays}, plus the allowed ones, minus the denied ones. Their demand score is the items delivered per
 *     day over the last {@code recentDays} against the items per day over the whole history; the lowest score, the
 *     material players buy least lately, ranks first.</li>
 *     <li><b>Price</b> is the market average times the factor, which rises by {@code repriceStep} with each reprice
 *     up to {@code maxFactor}, moved by the jitter and kept within the bounds. Underbidding orders pay at most
 *     {@code 1 - margin} of the lowest open player price and of the market average.</li>
 *     <li><b>Size</b> is {@code sizeShare} of the typical delivered amount, rounded to whole stacks, within
 *     {@code maxItems}.</li>
 *     <li><b>How many</b>: guessed orders stay at most {@code share} of all open orders, but at least
 *     {@code minOpen} and never more than {@code maxOpen}.</li>
 * </ul>
 */
public final class GuessedChoice {
    private GuessedChoice() {}

    /**
     * Ranks the candidates.
     *
     * @param settings the guessed order settings
     * @param volumes  the delivered volume per material over the history
     * @return the candidates, lowest demand score first
     */
    public static List<Candidate> rank(GuessedSettings settings, List<DeliveredVolume> volumes) {
        var deny = settings.deny();
        var allow = new HashMap<String, Boolean>();
        settings.allow().forEach(material -> allow.put(material, true));
        var candidates = new ArrayList<Candidate>();
        for (var volume : volumes) {
            var material = volume.material().toUpperCase(Locale.ROOT);
            var allowed = allow.remove(material) != null;
            if (deny.contains(material)) continue;
            if (volume.orders() < settings.minOrders() && !allowed) continue;
            var longRate = (double) volume.items() / settings.historyDays();
            var recentRate = (double) volume.recentItems() / settings.recentDays();
            var score = longRate > 0 ? recentRate / longRate : 0;
            var typical = volume.orders() > 0 ? (int) Math.round((double) volume.items() / volume.orders()) : 0;
            candidates.add(new Candidate(material, volume.orders(), longRate, recentRate, score, typical));
        }
        allow.keySet().stream()
                .filter(material -> !deny.contains(material))
                .forEach(material -> candidates.add(new Candidate(material, 0, 0, 0, 0, 0)));
        candidates.sort(Comparator.comparingDouble(Candidate::score).thenComparing(Candidate::material));
        return candidates;
    }

    /**
     * Prices a guessed order.
     *
     * @param settings the guessed order settings
     * @param average  the market average per item
     * @param step     how often the order was repriced before, 0 for a new one
     * @param demand   the open player demand of the material, null when there is none
     * @param random   a random number from 0 included to 1 excluded
     * @return the price, empty when it comes out below a cent
     */
    public static Optional<Price> price(
            GuessedSettings settings, double average, int step, @Nullable PlayerDemand demand, DoubleSupplier random) {
        var factor = Math.min(settings.maxFactor(), settings.factor() + step * settings.repriceStep());
        var rule = new RulePrice(
                RulePrice.PriceMode.AVERAGE, 0, factor, settings.jitter(), settings.minPrice(), settings.maxPrice());
        var priced = RulePricing.price(rule, OptionalDouble.of(average), random);
        if (priced.isEmpty()) return Optional.empty();
        var unit = priced.get().unit();
        Double cap = null;
        Double lowest = demand == null ? null : demand.lowestPrice();
        if (settings.competition() == GuessedSettings.Competition.UNDERBID) {
            var reference = lowest == null ? average : Math.min(average, lowest);
            cap = cap(reference, settings.margin());
            unit = Math.min(unit, cap);
        }
        if (unit < 0.01) return Optional.empty();
        return Optional.of(new Price(
                average, factor, step, priced.get().jitter(), priced.get().bounded(), lowest, cap, unit));
    }

    /**
     * How many items a guessed order asks for.
     *
     * @param settings the guessed order settings
     * @param typical  the typical delivered amount of the material, 0 when unknown
     * @param stack    the stack size of the material
     * @return the amount, in whole stacks where {@code maxItems} allows
     */
    public static int amount(GuessedSettings settings, int typical, int stack) {
        var size = Math.max(1, stack);
        var wanted = typical > 0 ? typical * settings.sizeShare() : size;
        var amount = Math.max(1, (int) Math.round(wanted / size)) * size;
        if (amount <= settings.maxItems()) return amount;
        var whole = settings.maxItems() / size * size;
        return whole > 0 ? whole : settings.maxItems();
    }

    /**
     * How many guessed orders should be open.
     *
     * @param settings   the guessed order settings
     * @param openPlayer open orders of players
     * @return the target, between {@code minOpen} and {@code maxOpen}
     */
    public static int target(GuessedSettings settings, int openPlayer) {
        var share = settings.share();
        var byShare = share <= 0 ? 0 : (int) Math.floor(share * openPlayer / (1 - share));
        return Math.min(settings.maxOpen(), Math.max(settings.minOpen(), byShare));
    }

    /**
     * The highest price per item an underbidding order may pay.
     *
     * @param reference the lower of the market average and the lowest open player price
     * @param margin    how much less it pays
     * @return {@code reference * (1 - margin)}, rounded down to cents
     */
    static double cap(double reference, double margin) {
        return BigDecimal.valueOf(reference)
                .multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(margin)))
                .setScale(2, RoundingMode.FLOOR)
                .doubleValue();
    }

    /**
     * A material the server may guess.
     *
     * @param material   the material, upper case
     * @param orders     delivered player orders in the history
     * @param longRate   items delivered per day over the history
     * @param recentRate items delivered per day lately
     * @param score      recent rate against long rate, lower is less demand
     * @param typical    items per delivered order, 0 when unknown
     */
    public record Candidate(
            String material, int orders, double longRate, double recentRate, double score, int typical) {}

    /**
     * The price of a guessed order and how it was found.
     *
     * @param average the market average per item
     * @param factor  the factor on it
     * @param step    the reprice step
     * @param jitter  the share drawn within the jitter
     * @param bounded whether the minimum or maximum price changed it
     * @param lowest  the lowest open player price per item, null without open player orders
     * @param cap     the highest price an underbidding order may pay, null when not underbidding
     * @param unit    the price per item
     */
    public record Price(
            double average,
            double factor,
            int step,
            double jitter,
            boolean bounded,
            @Nullable Double lowest,
            @Nullable Double cap,
            double unit) {}
}
