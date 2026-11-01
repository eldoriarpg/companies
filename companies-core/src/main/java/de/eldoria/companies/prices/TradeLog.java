/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.components.price.ShopTrade;
import de.eldoria.companies.data.repository.APriceData;
import de.eldoria.companies.data.wrapper.price.TradeDay;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects the trades of the shops in memory and adds them to the stored totals of their day on {@link #flush()}, so
 * a busy shop costs one write per material and minute rather than one per trade. Safe to use from any thread.
 */
public final class TradeLog {
    private final APriceData prices;
    private final Supplier<LocalDate> today;
    private final Logger logger;
    private static final int FAILURES_BEFORE_SINGLE_ROWS = 3;

    private Map<Key, TradeDay> pending = new HashMap<>();
    private final Object flushing = new Object();
    private int failures;

    /**
     * @param prices the stored prices
     * @param today  the current day, in UTC
     * @param logger reports failed writes
     */
    public TradeLog(APriceData prices, Supplier<LocalDate> today, Logger logger) {
        this.prices = prices;
        this.today = today;
        this.logger = logger;
    }

    /**
     * Notes a trade of a source. A trade of an item that is not plain or without a valid price is dropped.
     *
     * @param source id of the source, lower case
     * @param trade  the trade
     */
    public void trade(String source, ShopTrade trade) {
        if (trade == null || trade.side() == null || trade.origin() == null) return;
        if (!ShopItems.valid(trade.amount(), trade.price())) return;
        var material = ShopItems.material(trade.item());
        if (material.isEmpty()) return;
        var unit = trade.price() / trade.amount();
        var day = new TradeDay(
                today.get(),
                source,
                material.get(),
                trade.side(),
                trade.origin(),
                1,
                trade.amount(),
                trade.price(),
                unit,
                unit);
        var key = new Key(day.day(), source, day.material(), day.side(), day.origin());
        synchronized (this) {
            pending.merge(key, day, TradeDay::plus);
        }
    }

    /**
     * Adds the trades noted since the last flush to the stored totals. When the write fails they are kept for the
     * next one. After a few failures in a row each total is written alone; when some are stored that way, the totals
     * that still fail are dropped with a warning, so one bad row does not hold back the rest, while a database that
     * stores none keeps them all.
     */
    public void flush() {
        synchronized (flushing) {
            Map<Key, TradeDay> taken;
            synchronized (this) {
                if (pending.isEmpty()) return;
                taken = pending;
                pending = new HashMap<>();
            }
            List<TradeDay> days = new ArrayList<>(taken.values());
            boolean stored;
            try {
                stored = prices.addTrades(days);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not store shop trades", e);
                stored = false;
            }
            if (stored) {
                failures = 0;
                return;
            }
            if (++failures >= FAILURES_BEFORE_SINGLE_ROWS) {
                failures = 0;
                var before = taken.size();
                taken.entrySet().removeIf(entry -> storedAlone(entry.getValue()));
                if (taken.isEmpty()) return;
                if (taken.size() < before) {
                    logger.warning("Dropped " + taken.size() + " shop trade totals that could not be stored.");
                    return;
                }
            }
            synchronized (this) {
                taken.forEach((key, day) -> pending.merge(key, day, TradeDay::plus));
            }
        }
    }

    private boolean storedAlone(TradeDay day) {
        try {
            return prices.addTrades(List.of(day));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private record Key(LocalDate day, String source, String material, ShopSide side, ShopOrigin origin) {}
}
