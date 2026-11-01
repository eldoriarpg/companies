/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import de.eldoria.companies.components.price.PriceBasis;
import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.configuration.elements.prices.PriceSettings;
import de.eldoria.companies.data.wrapper.price.Baseline;
import de.eldoria.companies.data.wrapper.price.QuoteGroup;
import de.eldoria.companies.data.wrapper.price.TradeDay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The baseline rule: the most trusted kind of price with enough data per side, the midpoint of the sides, the clamp to
 * the server's prices, and the sources and origins left out.
 */
class BaselinesTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);
    private static final LocalDateTime NOW = DAY.atTime(12, 0);

    private final PriceSettings settings = new PriceSettings();

    @Test
    void tradesWinOverListingsAndServerPricesOnceThereAreEnough() {
        var baseline = only(Baselines.compute(
                List.of(trade("chestshop", ShopSide.SELL, ShopOrigin.PLAYER, 5, 10, 20)),
                List.of(
                        quote("chestshop", ShopSide.SELL, ShopOrigin.PLAYER, 3, 5),
                        quote("chestshop", ShopSide.SELL, ShopOrigin.ADMIN, 1, 1)),
                settings));

        assertEquals(2.0, baseline.unit(), 1e-9);
        assertEquals(2.0, baseline.sellUnit(), 1e-9);
        assertNull(baseline.buyUnit());
        assertEquals(PriceBasis.TRADES, baseline.basis());
    }

    @Test
    void tooFewTradesFallBackToListingsAndThenToServerPrices() {
        var listings = only(Baselines.compute(
                List.of(trade("chestshop", ShopSide.BUY, ShopOrigin.PLAYER, 4, 10, 20)),
                List.of(quote("chestshop", ShopSide.BUY, ShopOrigin.PLAYER, 3, 3)),
                settings));
        var server = only(Baselines.compute(
                List.of(),
                List.of(
                        quote("chestshop", ShopSide.BUY, ShopOrigin.PLAYER, 2, 3),
                        quote("shopgui", ShopSide.BUY, ShopOrigin.SERVER, 1, 4)),
                settings));

        assertEquals(3.0, listings.unit(), 1e-9);
        assertEquals(PriceBasis.LISTINGS, listings.basis());
        assertEquals(4.0, server.unit(), 1e-9);
        assertEquals(PriceBasis.SERVER, server.basis());
        assertEquals(List.of("shopgui"), server.sources());
    }

    @Test
    void theBaselineIsTheMidpointOfBothSidesAndNamesTheWeakerBasis() {
        var baseline = only(Baselines.compute(
                List.of(trade("chestshop", ShopSide.SELL, ShopOrigin.PLAYER, 5, 10, 10)),
                List.of(quote("chestshop", ShopSide.BUY, ShopOrigin.PLAYER, 3, 3)),
                settings));

        assertEquals(2.0, baseline.unit(), 1e-9);
        assertEquals(PriceBasis.LISTINGS, baseline.basis());
        assertEquals(List.of("chestshop"), baseline.sources());
    }

    @Test
    void aLoneSideIsMovedByItsFactor() {
        settings.sellFactor(1.5);
        var baseline = only(Baselines.compute(
                List.of(trade("chestshop", ShopSide.SELL, ShopOrigin.PLAYER, 5, 10, 20)), List.of(), settings));

        assertEquals(3.0, baseline.unit(), 1e-9);
    }

    @Test
    void serverPricesBoundWhatPlayerTradesDragTheBaselineTo() {
        var high = only(Baselines.compute(
                List.of(trade("chestshop", ShopSide.BUY, ShopOrigin.PLAYER, 10, 10, 1000)),
                List.of(
                        quote("shopgui", ShopSide.SELL, ShopOrigin.SERVER, 1, 1),
                        quote("shopgui", ShopSide.BUY, ShopOrigin.SERVER, 1, 5)),
                settings));
        var low = only(Baselines.compute(
                List.of(trade("chestshop", ShopSide.SELL, ShopOrigin.PLAYER, 10, 10, 1)),
                List.of(quote("shopgui", ShopSide.SELL, ShopOrigin.SERVER, 1, 2)),
                settings));

        assertEquals(5.0, high.unit(), 1e-9);
        assertEquals(List.of("chestshop", "shopgui"), high.sources());
        assertEquals(2.0, low.unit(), 1e-9);
        assertEquals(List.of("chestshop", "shopgui"), low.sources());
    }

    @Test
    void serverPricesWhoseSellLiesAboveTheirBuyDoNotClamp() {
        var baseline = only(Baselines.compute(
                List.of(
                        trade("chestshop", ShopSide.SELL, ShopOrigin.PLAYER, 5, 10, 10),
                        trade("chestshop", ShopSide.BUY, ShopOrigin.PLAYER, 5, 10, 30)),
                List.of(
                        quote("shopgui", ShopSide.SELL, ShopOrigin.SERVER, 1, 9),
                        quote("shopgui", ShopSide.BUY, ShopOrigin.SERVER, 1, 4)),
                settings));

        assertEquals(2.0, baseline.unit(), 1e-9);
    }

    @Test
    void aDayOfFewTradesMovesTheMedianLessThanADayOfMany() {
        var baseline = only(Baselines.compute(
                List.of(
                        trade("chestshop", ShopSide.SELL, ShopOrigin.PLAYER, 9, 9, 18),
                        new TradeDay(
                                DAY.minusDays(1),
                                "chestshop",
                                "STONE",
                                ShopSide.SELL,
                                ShopOrigin.PLAYER,
                                1,
                                1,
                                500,
                                500,
                                500)),
                List.of(),
                settings));

        assertEquals(2.0, baseline.unit(), 1e-9);
    }

    @Test
    void switchedOffSourcesAndThePlayersOfAdminOnlySourcesDoNotCount() {
        settings.sources(Map.of("shopgui", false));
        settings.adminOnly(List.of("ChestShop"));

        var baselines = Baselines.compute(
                List.of(trade("chestshop", ShopSide.SELL, ShopOrigin.PLAYER, 5, 10, 20)),
                List.of(
                        quote("shopgui", ShopSide.SELL, ShopOrigin.SERVER, 1, 9),
                        quote("chestshop", ShopSide.BUY, ShopOrigin.ADMIN, 1, 4)),
                settings);

        var baseline = only(baselines);
        assertEquals(4.0, baseline.unit(), 1e-9);
        assertNull(baseline.sellUnit());
        assertEquals(PriceBasis.SERVER, baseline.basis());
    }

    @Test
    void everyZShopEconomyCountsUntilSomeAreNamedIgnoringCase() {
        assertTrue(settings.zshopEconomy("tokens"));

        settings.zshopEconomies(List.of("Money"));

        assertTrue(settings.zshopEconomy("MONEY"));
        assertFalse(settings.zshopEconomy("tokens"));
    }

    @Test
    void theWeightedMedianTakesTheLowerOfTwoMiddles() {
        assertEquals(
                1.0,
                Baselines.median(List.of(new Baselines.Weighted(3, 1, "a"), new Baselines.Weighted(1, 1, "b"))),
                1e-9);
        assertNull(Baselines.median(List.of()));
        assertTrue(Baselines.compute(List.of(), List.of(), settings).isEmpty());
    }

    private static Baseline only(List<Baseline> baselines) {
        assertEquals(1, baselines.size(), baselines.toString());
        return baselines.getFirst();
    }

    private static TradeDay trade(
            String source, ShopSide side, ShopOrigin origin, int trades, long items, double total) {
        return new TradeDay(DAY, source, "STONE", side, origin, trades, items, total, 0, 0);
    }

    private static QuoteGroup quote(String source, ShopSide side, ShopOrigin origin, int quotes, double median) {
        return new QuoteGroup(source, "STONE", side, origin, quotes, quotes, median, median, median, NOW);
    }
}
