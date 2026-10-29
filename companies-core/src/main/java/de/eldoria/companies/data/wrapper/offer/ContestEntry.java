/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * A company in a contest. It keeps its delivered items and its place after it left.
 *
 * @param offer        id of the contest
 * @param company      id of the company
 * @param name         name of the company, null when it no longer exists
 * @param delivered    items the company delivered
 * @param paid         what its deliveries were paid before tax
 * @param joined       when it joined, the last time
 * @param left         when it left, null while it takes part
 * @param lastDelivery when it delivered last, null before its first delivery
 */
public record ContestEntry(
        int offer,
        int company,
        @Nullable String name,
        int delivered,
        double paid,
        LocalDateTime joined,
        @Nullable LocalDateTime left,
        @Nullable LocalDateTime lastDelivery) {
    /**
     * @return whether the company takes part and holds one of its order slots for it
     */
    public boolean active() {
        return left == null;
    }
}
