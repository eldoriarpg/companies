/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.area;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An area a company uses, stored in {@code company_areas}: a plot or region of an area provider, on the server whose
 * world holds it. An area belongs to one company at a time.
 *
 * @param id        id of the area, 0 before it is stored
 * @param company   id of the company
 * @param provider  id of the area provider, lower case
 * @param ref       the provider's reference of the area
 * @param world     the world it lies in
 * @param node      the server whose world holds it
 * @param source    how the company got it
 * @param linkedBy  the member who linked it, null unless it is linked
 * @param price     what the company paid for it, or pays per period of rent; 0 for a linked area
 * @param rentUntil until when a rented area is paid, null unless it is rented
 * @param created   when the company got it
 */
public record CompanyArea(
        int id,
        int company,
        String provider,
        String ref,
        String world,
        UUID node,
        AreaSource source,
        @Nullable UUID linkedBy,
        double price,
        @Nullable LocalDateTime rentUntil,
        LocalDateTime created) {}
