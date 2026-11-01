/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import de.eldoria.companies.configuration.elements.prices.PriceSettings;
import de.eldoria.companies.data.repository.APriceData;
import de.eldoria.companies.data.wrapper.price.QuoteGroup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the shop prices of this server stored: every minute it adds the noted trades to their days, and every
 * {@code sampleMinutes} it samples the offers of the shops on the main thread and replaces the server's stored
 * sample. {@link #refreshBaselines()} works out the baselines from what every server stored; the primary runs it with
 * the market prices.
 */
public final class ShopPrices {
    private static final long CHECK_SECONDS = 60;

    private final PriceSources sources;
    private final TradeLog trades;
    private final APriceData prices;
    private final Supplier<PriceSettings> settings;
    private final UUID node;
    private final Supplier<LocalDateTime> clock;
    private final Executor mainThread;
    private final Executor worker;
    private final Logger logger;
    private final Map<String, List<QuoteGroup>> lastGood = new ConcurrentHashMap<>();
    private volatile LocalDateTime lastSample = LocalDateTime.MIN;
    private volatile boolean sampling;

    /**
     * @param sources    the price sources of this server
     * @param trades     the trades noted on this server
     * @param prices     the stored prices
     * @param settings   the settings of shop prices, read on every use
     * @param node       this server
     * @param clock      the current time, in UTC
     * @param mainThread runs the sampling of the shops
     * @param worker     stores the samples, off the main thread
     * @param logger     reports failures
     */
    public ShopPrices(
            PriceSources sources,
            TradeLog trades,
            APriceData prices,
            Supplier<PriceSettings> settings,
            UUID node,
            Supplier<LocalDateTime> clock,
            Executor mainThread,
            Executor worker,
            Logger logger) {
        this.sources = sources;
        this.trades = trades;
        this.prices = prices;
        this.settings = settings;
        this.node = node;
        this.clock = clock;
        this.mainThread = mainThread;
        this.worker = worker;
        this.logger = logger;
    }

    /**
     * Starts storing trades and sampling offers, the first sample after a minute.
     *
     * @param executor runs the checks
     */
    public void schedule(ScheduledExecutorService executor) {
        lastSample = clock.get().minusMinutes(settings.get().sampleMinutes()).plusSeconds(CHECK_SECONDS);
        executor.scheduleWithFixedDelay(this::tick, CHECK_SECONDS, CHECK_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Stores the noted trades and samples the shops when a sample is due.
     */
    public void tick() {
        try {
            trades.flush();
            var now = clock.get();
            if (!sampling && !now.isBefore(lastSample.plusMinutes(settings.get().sampleMinutes()))) sample(now);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not store shop prices", e);
        }
    }

    /**
     * Stores the trades noted so far, as the plugin shuts down.
     */
    public void flush() {
        trades.flush();
    }

    private void sample(LocalDateTime now) {
        sampling = true;
        lastSample = now;
        CompletableFuture.supplyAsync(sources::sample, mainThread)
                .thenAcceptAsync(sampled -> store(sampled, now), worker)
                .whenComplete((done, error) -> {
                    sampling = false;
                    if (error != null) logger.log(Level.WARNING, "Could not sample the shop prices", error);
                });
    }

    /**
     * Stores a sample. A source that failed keeps its groups of the last sample it gave, so one failure does not drop
     * its prices; they age out like any sample.
     */
    private void store(PriceSources.Sample sampled, LocalDateTime now) {
        List<QuoteGroup> groups = new ArrayList<>();
        var fresh = QuoteSamples.groups(sampled.quotes(), now);
        for (var source : sampled.quotes().keySet()) {
            lastGood.put(
                    source,
                    fresh.stream()
                            .filter(group -> group.source().equals(source))
                            .toList());
        }
        lastGood.keySet()
                .removeIf(source -> !sampled.quotes().containsKey(source)
                        && !sampled.failed().contains(source));
        groups.addAll(fresh);
        for (var source : sampled.failed()) groups.addAll(lastGood.getOrDefault(source, List.of()));
        prices.replaceQuotes(node, groups);
    }

    /**
     * Works out the baselines from the trades of the last {@code days} days and the samples taken in them, and drops
     * older trades.
     */
    public void refreshBaselines() {
        var current = settings.get();
        var now = clock.get();
        var from = firstDay(now, current);
        prices.purgeTrades(from);
        var baselines = Baselines.compute(prices.trades(from), prices.quotes(oldestSample(now, current)), current);
        prices.replaceBaselines(baselines, now);
    }

    /**
     * @param now      the current time, in UTC
     * @param settings the settings of shop prices
     * @return the first day whose trades count: {@code days} days, today included
     */
    public static LocalDate firstDay(LocalDateTime now, PriceSettings settings) {
        return now.toLocalDate().minusDays(settings.days() - 1L);
    }

    /**
     * @param now      the current time, in UTC
     * @param settings the settings of shop prices
     * @return the time of the oldest sample of offers that counts
     */
    public static LocalDateTime oldestSample(LocalDateTime now, PriceSettings settings) {
        return now.minusDays(settings.days());
    }
}
