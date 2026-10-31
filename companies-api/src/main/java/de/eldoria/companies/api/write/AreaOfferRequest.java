/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import de.eldoria.companies.components.area.OfferKind;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * An area to offer through {@link de.eldoria.companies.api.AreaApi#offer(AreaOfferRequest)}, with the same checks as
 * {@code /companyadmin area offer}. The area is named by a location on this server, or by its plugin and reference.
 *
 * @param location   where the area lies, copied; null when named by plugin and reference
 * @param provider   the id of the area plugin, null when named by location
 * @param ref        the plugin's reference of the area, null when named by location
 * @param kind       whether companies buy or rent it
 * @param price      the price of a sale, or the rent per period; rounded down to cents
 * @param periodDays the days one rent period lasts, 0 for a sale
 * @param by         who offers it, for the server log
 */
public record AreaOfferRequest(
        @Nullable Location location,
        @Nullable String provider,
        @Nullable String ref,
        OfferKind kind,
        double price,
        int periodDays,
        String by) {
    /**
     * The highest price or rent of an offer.
     */
    public static final double MAX_PRICE = 1.0E12;

    /**
     * The longest rent period, in days.
     */
    public static final int MAX_PERIOD_DAYS = 3650;

    /**
     * Validates the request.
     *
     * @throws IllegalArgumentException when the area is named both ways or not at all, the price is not between 0 and
     *                                  {@link #MAX_PRICE}, or the rent period is out of 1 to {@link #MAX_PERIOD_DAYS}
     *                                  days (or not 0 for a sale)
     */
    public AreaOfferRequest {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(by, "by");
        if ((location == null) == (provider == null || ref == null)) {
            throw new IllegalArgumentException("Name the area by a location or by provider and reference");
        }
        if (location != null && location.getWorld() == null) {
            throw new IllegalArgumentException("The location needs a world");
        }
        if (!Double.isFinite(price) || price < 0 || price > MAX_PRICE) {
            throw new IllegalArgumentException("The price must be between 0 and " + MAX_PRICE);
        }
        if (kind == OfferKind.RENT && (periodDays < 1 || periodDays > MAX_PERIOD_DAYS)) {
            throw new IllegalArgumentException("A rent period lasts 1 to " + MAX_PERIOD_DAYS + " days");
        }
        if (kind == OfferKind.BUY && periodDays != 0) throw new IllegalArgumentException("A sale has no period");
        location = location == null ? null : location.clone();
    }

    /**
     * Offers the area at a location.
     *
     * @param location   where the area lies, on this server
     * @param kind       whether companies buy or rent it
     * @param price      the price, or the rent per period
     * @param periodDays the days one rent period lasts, 0 for a sale
     * @param by         who offers it
     * @return the request
     */
    public static AreaOfferRequest at(Location location, OfferKind kind, double price, int periodDays, String by) {
        return new AreaOfferRequest(location, null, null, kind, price, periodDays, by);
    }

    /**
     * Offers an area by its plugin and reference.
     *
     * @param provider   the id of the area plugin
     * @param ref        the plugin's reference of the area
     * @param kind       whether companies buy or rent it
     * @param price      the price, or the rent per period
     * @param periodDays the days one rent period lasts, 0 for a sale
     * @param by         who offers it
     * @return the request
     */
    public static AreaOfferRequest of(
            String provider, String ref, OfferKind kind, double price, int periodDays, String by) {
        return new AreaOfferRequest(null, provider, ref, kind, price, periodDays, by);
    }
}
