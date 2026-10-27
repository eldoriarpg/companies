/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.systemorders;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orders the server guesses from the market data, the {@code guessed} section of {@code system-orders.yml}. They fill
 * gaps and never compete with players: an item players want is skipped, or paid clearly less.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class GuessedSettings {
    /**
     * The rule id guessed orders are stored under. A defined rule may not use it.
     */
    public static final String RULE = "guessed";

    private boolean enabled = false;
    private int historyDays = 14;
    private int recentDays = 3;
    private int minOrders = 3;
    private List<String> allow = List.of();
    private List<String> deny = List.of();
    private Competition competition = Competition.SKIP;
    private double margin = 0.3;
    private double factor = 0.8;
    private double maxFactor = 1.0;
    private double jitter = 0.05;
    private double minPrice = 0.0;
    private double maxPrice = 0.0;
    private double sizeShare = 0.5;
    private int maxItems = 512;
    private int repriceAfterHours = 2;
    private double repriceStep = 0.05;
    private int maxReprices = 3;
    private int restHours = 24;
    private double share = 0.2;
    private int minOpen = 3;
    private int maxOpen = 10;
    private int perCompanyDaily = 5;
    private int perPlayerDaily = 5;
    private MoneySource source = new MoneySource();
    private LearningSettings learning = new LearningSettings();

    public GuessedSettings() {}

    /**
     * @return whether the server places guessed orders
     */
    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return days of market history a material is judged by, at least 2
     */
    public int historyDays() {
        return Math.max(2, historyDays);
    }

    /**
     * @return the last days of the history that count as recent, at least 1 and fewer than {@link #historyDays()}
     */
    public int recentDays() {
        return Math.clamp(recentDays, 1, historyDays() - 1);
    }

    /**
     * @return delivered orders a material needs in the history to be guessed, at least 1
     */
    public int minOrders() {
        return Math.max(1, minOrders);
    }

    public void minOrders(int minOrders) {
        this.minOrders = minOrders;
    }

    /**
     * @return materials guessed even with fewer delivered orders, upper case
     */
    public Set<String> allow() {
        return names(allow);
    }

    public void allow(List<String> allow) {
        this.allow = List.copyOf(allow);
    }

    /**
     * @return materials never guessed, upper case
     */
    public Set<String> deny() {
        return names(deny);
    }

    public void deny(List<String> deny) {
        this.deny = List.copyOf(deny);
    }

    /**
     * @return how guessed orders keep out of the way of players
     */
    public Competition competition() {
        return competition == null ? Competition.SKIP : competition;
    }

    public void competition(Competition competition) {
        this.competition = competition;
    }

    /**
     * @return how much less than players and the market an underbidding order pays, between 0.05 and 0.95
     */
    public double margin() {
        return Math.clamp(margin, 0.05, 0.95);
    }

    /**
     * @return the factor on the market average a new guessed order pays, above 0
     */
    public double factor() {
        return Math.max(0.01, factor);
    }

    public void factor(double factor) {
        this.factor = factor;
    }

    /**
     * @return the highest factor repricing may reach, never below {@link #factor()}
     */
    public double maxFactor() {
        return Math.max(factor(), maxFactor);
    }

    /**
     * @return how far the price moves at random either way, between 0 and 0.9
     */
    public double jitter() {
        return Math.clamp(jitter, 0, 0.9);
    }

    public void jitter(double jitter) {
        this.jitter = jitter;
    }

    /**
     * @return the lowest price per item, never negative
     */
    public double minPrice() {
        return Math.max(0, minPrice);
    }

    public void minPrice(double minPrice) {
        this.minPrice = minPrice;
    }

    /**
     * @return the highest price per item, 0 for none
     */
    public double maxPrice() {
        return Math.max(0, maxPrice);
    }

    /**
     * @return the share of the typical delivered amount one order asks for, between 0.05 and 1
     */
    public double sizeShare() {
        return Math.clamp(sizeShare, 0.05, 1);
    }

    /**
     * @return the most items one order asks for, at least 1
     */
    public int maxItems() {
        return Math.max(1, maxItems);
    }

    public void maxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    /**
     * @return hours an order waits for a company before it is repriced, at least 1
     */
    public int repriceAfterHours() {
        return Math.max(1, repriceAfterHours);
    }

    public void repriceAfterHours(int repriceAfterHours) {
        this.repriceAfterHours = repriceAfterHours;
    }

    /**
     * @return how much the factor rises with each reprice, never negative
     */
    public double repriceStep() {
        return Math.max(0, repriceStep);
    }

    public void repriceStep(double repriceStep) {
        this.repriceStep = repriceStep;
    }

    /**
     * @return how often an order is repriced before it is removed, never negative
     */
    public int maxReprices() {
        return Math.max(0, maxReprices);
    }

    public void maxReprices(int maxReprices) {
        this.maxReprices = maxReprices;
    }

    /**
     * @return hours a material is not guessed after its last reprice found nobody, never negative
     */
    public int restHours() {
        return Math.max(0, restHours);
    }

    public void restHours(int restHours) {
        this.restHours = restHours;
    }

    /**
     * @return the share of all open orders guessed orders may make up, between 0 and 0.9
     */
    public double share() {
        return Math.clamp(share, 0, 0.9);
    }

    public void share(double share) {
        this.share = share;
    }

    /**
     * @return guessed orders kept open even when players order nothing, never negative
     */
    public int minOpen() {
        return Math.max(0, minOpen);
    }

    public void minOpen(int minOpen) {
        this.minOpen = minOpen;
    }

    /**
     * @return the most guessed orders open at once, never below {@link #minOpen()}
     */
    public int maxOpen() {
        return Math.max(minOpen(), maxOpen);
    }

    public void maxOpen(int maxOpen) {
        this.maxOpen = maxOpen;
    }

    /**
     * @return guessed orders one company may take per day, 0 for no limit
     */
    public int perCompanyDaily() {
        return Math.max(0, perCompanyDaily);
    }

    public void perCompanyDaily(int perCompanyDaily) {
        this.perCompanyDaily = perCompanyDaily;
    }

    /**
     * @return guessed orders one player working alone may take per day, 0 for no limit
     */
    public int perPlayerDaily() {
        return Math.max(0, perPlayerDaily);
    }

    public void perPlayerDaily(int perPlayerDaily) {
        this.perPlayerDaily = perPlayerDaily;
    }

    /**
     * @return where the money of guessed orders comes from
     */
    public MoneySource source() {
        return source == null ? new MoneySource() : source;
    }

    public void source(MoneySource source) {
        this.source = source;
    }

    /**
     * @return how guessed orders learn from their earlier orders
     */
    public LearningSettings learning() {
        return learning == null ? new LearningSettings() : learning;
    }

    public void learning(LearningSettings learning) {
        this.learning = learning;
    }

    private static Set<String> names(List<String> materials) {
        if (materials == null) return Set.of();
        return materials.stream()
                .filter(Objects::nonNull)
                .map(name -> name.trim().toUpperCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * How guessed orders keep out of the way of players.
     */
    public enum Competition {
        /**
         * No guessed order for a material players have an open order for; one already placed is withdrawn.
         */
        SKIP,
        /**
         * Guessed orders pay at most {@code 1 - margin} of the lowest open player price and of the market average.
         */
        UNDERBID
    }
}
