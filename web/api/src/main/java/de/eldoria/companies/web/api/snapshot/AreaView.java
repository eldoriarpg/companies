/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An area of a company: a plot or region its members build in.
 *
 * @param id        the area id
 * @param provider  the plugin the area comes from, such as {@code plotsquared}
 * @param ref       the plugin's name of the area
 * @param world     the world it lies in
 * @param source    how the company got it
 * @param price     what the company paid, or pays per period of rent; 0 for a linked area
 * @param rentUntil until when a rented area is paid, none unless it is rented
 * @param renews    whether a rented area is paid for another period when it is due
 */
public record AreaView(
        int id,
        String provider,
        String ref,
        String world,
        AreaSourceView source,
        double price,
        @Nullable Instant rentUntil,
        boolean renews) {}
