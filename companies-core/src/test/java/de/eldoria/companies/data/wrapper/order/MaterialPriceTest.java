/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.components.price.PriceBasis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which price Companies goes by: its own orders from {@code minOrders} on or when no shop knows the material, the
 * baseline of the shops below that.
 */
class MaterialPriceTest {
    @Test
    void enoughOrdersWinOverTheShops() {
        var price = new MaterialPrice("stone", 2, 1, 3, 3, 5, PriceBasis.TRADES, 3);

        assertTrue(price.fromOrders());
        assertEquals(2, price.price(), 1e-9);
        assertEquals(5, price.baseline().orElseThrow(), 1e-9);
    }

    @Test
    void belowMinOrdersTheShopsFillIn() {
        var price = new MaterialPrice("stone", 2, 1, 3, 2, 5, PriceBasis.LISTINGS, 3);

        assertFalse(price.fromOrders());
        assertEquals(5, price.price(), 1e-9);
        assertEquals(PriceBasis.LISTINGS, price.basis().orElseThrow());
    }

    @Test
    void theNumberOfOrdersGoneByCanChangeAfterThePriceWasRead() {
        var price = new MaterialPrice("stone", 2, 1, 3, 2, 5, PriceBasis.TRADES, 3);

        assertEquals(2, price.withMinOrders(2).price(), 1e-9);
        assertEquals(5, price.withMinOrders(3).price(), 1e-9);
    }

    @Test
    void fewOrdersStillCountWhenNoShopKnowsTheMaterial() {
        var price = new MaterialPrice("stone", 2, 1, 3, 1, 0, null, 3);

        assertTrue(price.fromOrders());
        assertEquals(2, price.price(), 1e-9);
        assertTrue(price.hasData());
    }

    @Test
    void aMaterialOnlyTheShopsKnowHasTheirPriceAndNothingHasNone() {
        var shops = MaterialPrice.ofShops("stone", 4, PriceBasis.SERVER);
        var nothing = new MaterialPrice("stone");

        assertFalse(shops.fromOrders());
        assertEquals(4, shops.price(), 1e-9);
        assertTrue(shops.hasData());
        assertFalse(nothing.hasData());
        assertTrue(nothing.basis().isEmpty());
    }
}
