/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.systemorders;

/**
 * How guessed orders learn from how their earlier orders went, the {@code guessed.learning} section of
 * {@code system-orders.yml}. Nothing is trained: it counts outcomes and moves prices in bounded steps.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class LearningSettings {
    private boolean enabled = true;
    private int window = 10;
    private int fastMinutes = 30;
    private double step = 0.05;
    private double minFactor = 0.5;
    private double weightFloor = 0.1;

    public LearningSettings() {}

    /**
     * @return whether guessed orders learn; without it they are chosen by rank at the configured factor
     */
    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return how many of a material's last ended orders move its price, at least 1
     */
    public int window() {
        return Math.max(1, window);
    }

    public void window(int window) {
        this.window = window;
    }

    /**
     * @return minutes within which a taken order counts as taken fast, at least 1
     */
    public int fastMinutes() {
        return Math.max(1, fastMinutes);
    }

    public void fastMinutes(int fastMinutes) {
        this.fastMinutes = fastMinutes;
    }

    /**
     * @return how much the factor moves per fast take or expiry, never negative
     */
    public double step() {
        return Math.max(0, step);
    }

    public void step(double step) {
        this.step = step;
    }

    /**
     * @return the lowest factor learning may reach, above 0
     */
    public double minFactor() {
        return Math.max(0.01, minFactor);
    }

    public void minFactor(double minFactor) {
        this.minFactor = minFactor;
    }

    /**
     * @return the lowest take rate a material is weighted with, so every candidate is still tried now and then,
     *         between 0.01 and 1
     */
    public double weightFloor() {
        return Math.clamp(weightFloor, 0.01, 1);
    }

    public void weightFloor(double weightFloor) {
        this.weightFloor = weightFloor;
    }
}
