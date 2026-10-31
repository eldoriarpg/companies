/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.data.wrapper.area.AreaSource;
import de.eldoria.companies.data.wrapper.area.CompanyArea;
import de.eldoria.companies.util.Money;

import java.math.RoundingMode;
import java.util.List;

/**
 * What the server pays back for bought areas: {@code sellBackPercent} of their price, rounded down to the cent.
 */
public final class AreaPrices {
    private AreaPrices() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param area    an area
     * @param percent the part of the price paid back, in percent
     * @return what the server pays back for the area in cents, 0 unless it was bought
     */
    public static long sellBack(CompanyArea area, int percent) {
        if (area.source() != AreaSource.BOUGHT) return 0;
        return Money.toCents(Money.percentOf(area.price(), percent).setScale(2, RoundingMode.FLOOR));
    }

    /**
     * @param areas   the areas of a company
     * @param percent the part of the price paid back, in percent
     * @return what the server pays back for the bought ones in cents
     */
    public static long sellBack(List<CompanyArea> areas, int percent) {
        return areas.stream().mapToLong(area -> sellBack(area, percent)).sum();
    }
}
