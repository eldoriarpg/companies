/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.data.wrapper.area.AreaSource;
import de.eldoria.companies.data.wrapper.area.CompanyArea;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the server pays back for bought areas: a part of their price, rounded down to the cent, and nothing for linked
 * or rented ones.
 */
class AreaPricesTest {
    @Test
    void onlyBoughtAreasArePaidBackRoundedDownToTheCent() {
        var areas = List.of(
                area(AreaSource.BOUGHT, 0.29),
                area(AreaSource.BOUGHT, 100),
                area(AreaSource.RENTED, 50),
                area(AreaSource.LINKED, 0));

        assertEquals(14 + 5000, AreaPrices.sellBack(areas, 50));
        assertEquals(0, AreaPrices.sellBack(areas, 0));
    }

    private static CompanyArea area(AreaSource source, double price) {
        return new CompanyArea(
                0,
                1,
                "plotsquared",
                "plots|1;1",
                "plots",
                UUID.randomUUID(),
                source,
                null,
                price,
                null,
                LocalDateTime.of(2026, 10, 31, 12, 0));
    }
}
