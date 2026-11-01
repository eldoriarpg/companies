/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.price.Baseline;
import de.eldoria.companies.data.wrapper.price.QuoteGroup;
import de.eldoria.companies.data.wrapper.price.SourceStats;
import de.eldoria.companies.data.wrapper.price.TradeDay;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The prices read from shop plugins: the daily totals of their trades, the latest sample of their offers per server,
 * and the baselines worked out from both. Nothing names a player. The methods block and are called off the main
 * thread.
 */
public abstract class APriceData {
    /**
     * Adds trades to the totals of their day, material, side, origin and source.
     *
     * @param trades the totals to add
     * @return true when they were stored
     */
    public abstract boolean addTrades(List<TradeDay> trades);

    /**
     * @param from the first day
     * @return the totals of the trades from that day on
     */
    public abstract List<TradeDay> trades(LocalDate from);

    /**
     * Removes the totals of trades before a day.
     *
     * @param before the first day kept
     * @return the number of rows removed
     */
    public abstract int purgeTrades(LocalDate before);

    /**
     * Replaces the sample of the offers of some sources on a server, so a shop that is gone is gone from the sample.
     * The samples of other sources stay, such as one that could not be sampled this time.
     *
     * @param node    the server
     * @param sources the ids of the sources sampled
     * @param quotes  what their offers came to, per source, material, side and origin
     * @return true when the sample was stored
     */
    public abstract boolean replaceQuotes(UUID node, Set<String> sources, List<QuoteGroup> quotes);

    /**
     * @param from the oldest sample read
     * @return the samples of every server taken from then on
     */
    public abstract List<QuoteGroup> quotes(LocalDateTime from);

    /**
     * Replaces the baselines of every material.
     *
     * @param baselines the baselines
     * @param now       the current time
     * @return true when they were stored
     */
    public abstract boolean replaceBaselines(List<Baseline> baselines, LocalDateTime now);

    /**
     * @return the baselines of every material, as last worked out
     */
    public abstract List<Baseline> baselines();

    /**
     * Sums up what each source stored, for admins to see which shop plugins give prices.
     *
     * @param from        the first day of trades counted
     * @param sampledFrom the oldest sample counted
     * @return one row per source with trades or offers, by id
     */
    public abstract List<SourceStats> sourceStats(LocalDate from, LocalDateTime sampledFrom);
}
