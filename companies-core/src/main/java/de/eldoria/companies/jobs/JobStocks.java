/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.repository.AJobData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The stock of the supplied build jobs worked on this server, kept in memory so placing a magic block takes from it on
 * the main thread. A job's stock is read once when its work starts here; every change is applied at once in memory and
 * written right after, one write after the other, so the database follows within moments. Only this server changes
 * the stock of a taken job, as its area lies here. A job that ends is closed first: its queued writes are finished
 * and nothing is taken from or put into its stock any more, so what is returned to its owner is what is left. A take
 * the database refuses drops the job's stock from memory, so it is read again.
 */
public final class JobStocks {
    private static final int STOP_SECONDS = 10;

    private final AJobData jobs;
    private final Logger logger;
    private final ExecutorService writer;
    private final Map<Integer, Map<String, Integer>> stocks = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> loading = new ConcurrentHashMap<>();
    private final Set<Integer> closed = ConcurrentHashMap.newKeySet();
    private volatile BiConsumer<Integer, Map<String, Integer>> late = (job, items) -> {};

    /**
     * @param jobs   the jobs and their stock
     * @param logger reports writes that failed
     */
    public JobStocks(AJobData jobs, Logger logger) {
        this(jobs, logger, Executors.newSingleThreadExecutor(Thread.ofVirtual().factory()));
    }

    /**
     * @param jobs   the jobs and their stock
     * @param logger reports writes that failed
     * @param writer runs the reads and writes one after the other
     */
    JobStocks(AJobData jobs, Logger logger, ExecutorService writer) {
        this.jobs = jobs;
        this.logger = logger;
        this.writer = writer;
    }

    /**
     * Reads the stock of a job when it is not read yet.
     *
     * @param job id of the job
     */
    public void load(int job) {
        if (closed.contains(job) || stocks.containsKey(job) || loading.putIfAbsent(job, true) != null) return;
        writer.execute(() -> {
            try {
                if (!closed.contains(job)) stocks.putIfAbsent(job, new ConcurrentHashMap<>(jobs.stock(job)));
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not read the stock of job " + job, e);
            } finally {
                loading.remove(job);
            }
        });
    }

    /**
     * @param job id of a job
     * @return its stock as amounts by material name, empty while it is not read yet
     */
    public Optional<Map<String, Integer>> of(int job) {
        var stock = stocks.get(job);
        return stock == null ? Optional.empty() : Optional.of(Map.copyOf(stock));
    }

    /**
     * Takes items out of a job's stock, all or none.
     *
     * @param job   id of the job
     * @param items the items, as amounts by material name
     * @return true when the stock is read and held them all
     */
    public synchronized boolean take(int job, Map<String, Integer> items) {
        var stock = stocks.get(job);
        if (stock == null || closed.contains(job)) return false;
        for (var item : items.entrySet()) {
            if (stock.getOrDefault(item.getKey(), 0) < item.getValue()) return false;
        }
        items.forEach((material, amount) -> stock.merge(material, -amount, Integer::sum));
        var taken = Map.copyOf(items);
        writer.execute(() -> taken.forEach((material, amount) -> {
            try {
                if (!jobs.takeStock(job, material, amount)) {
                    logger.warning("The stock of job " + job + " held less " + material + " than this server knew");
                    stocks.remove(job);
                }
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not take " + amount + " " + material + " from job " + job, e);
            }
        }));
        return true;
    }

    /**
     * Puts items back into a job's stock; for a closed job they go where {@link #late} sends them.
     *
     * @param job   id of the job
     * @param items the items, as amounts by material name
     * @return whether they went into the stock
     */
    public synchronized boolean give(int job, Map<String, Integer> items) {
        if (items.isEmpty()) return true;
        if (closed.contains(job)) {
            var given = Map.copyOf(items);
            writer.execute(() -> {
                try {
                    late.accept(job, given);
                } catch (RuntimeException e) {
                    logger.log(Level.SEVERE, "Could not return " + given + " of the ended job " + job, e);
                }
            });
            return false;
        }
        var stock = stocks.get(job);
        if (stock != null) items.forEach((material, amount) -> stock.merge(material, amount, Integer::sum));
        var given = new LinkedHashMap<>(items);
        writer.execute(() -> {
            try {
                jobs.stock(job, given);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not put " + given + " back into the stock of job " + job, e);
            }
        });
        return true;
    }

    /**
     * Sets where items go that come back after their job's stock was closed: its owner's item returns.
     *
     * @param late takes the items of a job, by its id
     */
    public void late(BiConsumer<Integer, Map<String, Integer>> late) {
        this.late = late;
    }

    /**
     * Closes the stock of a job that ends: waits for its queued writes and takes or gives nothing more. Blocks for as
     * long as the writes take.
     *
     * @param job id of the job
     */
    public void close(int job) {
        synchronized (this) {
            closed.add(job);
            stocks.remove(job);
        }
        try {
            writer.submit(() -> {}).get(STOP_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RejectedExecutionException e) {
            logger.log(Level.WARNING, "The stock of job " + job + " may not be written yet", e);
        }
    }

    /**
     * Forgets the stock of jobs no longer worked on here.
     *
     * @param working whether a job is still worked on, by its id
     */
    public void retain(IntPredicate working) {
        stocks.keySet().removeIf(job -> !working.test(job));
    }

    /**
     * Waits for the writes that are queued, before the plugin stops.
     */
    public void stop() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(STOP_SECONDS, TimeUnit.SECONDS)) {
                logger.warning("The stock of supplied jobs was not written in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
