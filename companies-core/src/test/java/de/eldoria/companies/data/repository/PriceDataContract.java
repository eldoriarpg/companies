/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.configuration.QueryConfiguration;
import de.eldoria.companies.components.price.PriceBasis;
import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.data.wrapper.price.Baseline;
import de.eldoria.companies.data.wrapper.price.QuoteGroup;
import de.eldoria.companies.data.wrapper.price.TradeDay;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every dialect does with shop prices: trades added to their day, samples replaced per server, baselines replaced
 * as a whole and read with the market price of a material.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PriceDataContract {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);
    private static final LocalDateTime NOW = DAY.atTime(12, 0);
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OTHER_NODE = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private DataSource dataSource;
    private APriceData prices;

    /**
     * Starts a migrated database.
     *
     * @return its data source
     */
    protected abstract DataSource start() throws Exception;

    /**
     * Stops the database.
     */
    protected abstract void stop();

    /**
     * @return the dialect's price tables
     */
    protected abstract APriceData prices();

    /**
     * @return the dialect's order tables, which read the baseline with the market price
     */
    protected abstract AOrderData orders();

    @BeforeAll
    void startDatabase() throws Exception {
        dataSource = start();
    }

    @AfterAll
    void stopDatabase() {
        if (dataSource != null) stop();
    }

    @BeforeEach
    void setUp() {
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource).build());
        query("DELETE FROM shop_trade_day").single().delete();
        query("DELETE FROM shop_quote").single().delete();
        query("DELETE FROM material_baseline").single().delete();
        prices = prices();
    }

    @Test
    void tradesAddUpToTheTotalsOfTheirDay() {
        assertTrue(prices.addTrades(List.of(trade(DAY, 2, 4, 8, 2, 2))));
        assertTrue(prices.addTrades(List.of(trade(DAY, 1, 1, 5, 5, 5), trade(DAY.minusDays(1), 1, 1, 1, 1, 1))));

        var today = prices.trades(DAY);
        assertEquals(1, today.size());
        var day = today.getFirst();
        assertEquals(3, day.trades());
        assertEquals(5, day.items());
        assertEquals(13, day.total(), 1e-9);
        assertEquals(2, day.minUnit(), 1e-9);
        assertEquals(5, day.maxUnit(), 1e-9);
        assertEquals(ShopSide.SELL, day.side());
        assertEquals(ShopOrigin.PLAYER, day.origin());
        assertEquals(2, prices.trades(DAY.minusDays(1)).size());

        assertEquals(1, prices.purgeTrades(DAY));
        assertEquals(1, prices.trades(DAY.minusDays(7)).size());
    }

    @Test
    void aSampleReplacesOnlyTheSampleOfItsServer() {
        prices.replaceQuotes(NODE, List.of(quote("STONE", NOW), quote("DIRT", NOW)));
        prices.replaceQuotes(OTHER_NODE, List.of(quote("STONE", NOW.minusDays(30))));
        prices.replaceQuotes(NODE, List.of(quote("SAND", NOW)));

        var recent = prices.quotes(NOW.minusDays(1));
        assertEquals(1, recent.size());
        var quote = recent.getFirst();
        assertEquals("SAND", quote.material());
        assertEquals(3, quote.quotes());
        assertEquals(1.5, quote.medianUnit(), 1e-9);
        assertEquals(NOW, quote.sampledAt());
        assertEquals(2, prices.quotes(NOW.minusDays(31)).size());
    }

    @Test
    void baselinesAreReplacedAsAWholeAndReadWithTheMarketPrice() {
        prices.replaceBaselines(List.of(new Baseline("DIRT", 1.0, null, 1.0, PriceBasis.TRADES)), NOW);
        assertTrue(prices.replaceBaselines(
                List.of(new Baseline("STONE", null, 3.0, 2.5, PriceBasis.SERVER)), NOW.plusHours(1)));

        var orders = orders();
        orders.minOrders(() -> 3);
        var stone = orders.retrieveMaterialPrice("stone").asFuture().join();
        var dirt = orders.retrieveMaterialPrice("dirt").asFuture().join();

        assertEquals(2.5, stone.baseline().orElseThrow(), 1e-9);
        assertEquals(PriceBasis.SERVER, stone.basis().orElseThrow());
        assertEquals(2.5, stone.price(), 1e-9);
        assertEquals(0, stone.orders());
        assertTrue(dirt.baseline().isEmpty());
        assertNull(dirt.basis().orElse(null));
        assertEquals(0, dirt.price(), 1e-9);
    }

    private static TradeDay trade(LocalDate day, int trades, long items, double total, double min, double max) {
        return new TradeDay(
                day, "chestshop", "STONE", ShopSide.SELL, ShopOrigin.PLAYER, trades, items, total, min, max);
    }

    private static QuoteGroup quote(String material, LocalDateTime sampledAt) {
        return new QuoteGroup("chestshop", material, ShopSide.BUY, ShopOrigin.PLAYER, 3, 6, 1.5, 1.0, 2.0, sampledAt);
    }
}
