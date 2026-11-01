/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import de.eldoria.companies.data.repository.APriceData;
import de.eldoria.companies.data.wrapper.price.Baseline;
import de.eldoria.companies.data.wrapper.price.QuoteGroup;
import de.eldoria.companies.data.wrapper.price.TradeDay;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shop prices held in memory, adding trades to their day like the database does.
 */
class MemoryPriceData extends APriceData {
    final Map<List<Object>, TradeDay> trades = new LinkedHashMap<>();
    final Map<UUID, List<QuoteGroup>> quotes = new HashMap<>();
    List<Baseline> baselines = List.of();
    boolean failing;

    @Override
    public synchronized boolean addTrades(List<TradeDay> added) {
        if (failing) return false;
        for (var trade : added) {
            trades.merge(
                    List.of(trade.day(), trade.source(), trade.material(), trade.side(), trade.origin()),
                    trade,
                    TradeDay::plus);
        }
        return true;
    }

    @Override
    public synchronized List<TradeDay> trades(LocalDate from) {
        return trades.values().stream()
                .filter(trade -> !trade.day().isBefore(from))
                .toList();
    }

    @Override
    public synchronized int purgeTrades(LocalDate before) {
        var size = trades.size();
        trades.values().removeIf(trade -> trade.day().isBefore(before));
        return size - trades.size();
    }

    @Override
    public synchronized boolean replaceQuotes(UUID node, List<QuoteGroup> groups) {
        quotes.put(node, List.copyOf(groups));
        return true;
    }

    @Override
    public synchronized List<QuoteGroup> quotes(LocalDateTime from) {
        List<QuoteGroup> all = new ArrayList<>();
        quotes.values().forEach(all::addAll);
        return all.stream().filter(quote -> !quote.sampledAt().isBefore(from)).toList();
    }

    @Override
    public synchronized boolean replaceBaselines(List<Baseline> replaced, LocalDateTime now) {
        baselines = List.copyOf(replaced);
        return true;
    }
}
