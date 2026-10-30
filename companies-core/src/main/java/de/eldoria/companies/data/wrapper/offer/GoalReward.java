/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import org.jetbrains.annotations.Nullable;

/**
 * The reward of a goal: a short reward multiplier server event created when the goal is reached.
 *
 * @param value   the factor of the multiplier, above 1
 * @param minutes how long the event runs
 * @param funding where its money comes from, null for the default of {@code events.yml}
 * @param account the account paying it, null for the default
 * @param budget  what it may pay in all, null for the default
 */
public record GoalReward(
        double value,
        int minutes,
        MoneySource.@Nullable SourceType funding,
        @Nullable String account,
        @Nullable Double budget) {}
