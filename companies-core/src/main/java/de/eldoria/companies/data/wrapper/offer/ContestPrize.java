/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

import org.jetbrains.annotations.Nullable;

/**
 * A prize of a settled contest.
 *
 * @param place   the place, from 1
 * @param amount  the money of the place
 * @param company id of the company that won it, null when no company reached the place
 * @param name    name of that company, null when unknown
 * @param state   where the prize is
 */
public record ContestPrize(
        int place,
        double amount,
        @Nullable Integer company,
        @Nullable String name,
        PrizeState state) {
    /**
     * @return whether the money reached the company
     */
    public boolean paid() {
        return state == PrizeState.PAID;
    }
}
