/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import de.eldoria.companies.orders.query.OrderFilter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the open order counts on the board signs of this server current.
 * <p>
 * {@link #tick()} runs every second on the main thread and starts a refresh when the {@link RefreshSchedule} says so.
 * A refresh counts every distinct board of the index once, off the main thread, and then writes the signs on the main
 * thread, but only those whose count changed since it was last written. A sign that is no board any more is dropped
 * from the index. One refresh runs at a time.
 */
public final class SignBoardRefresher {
    private final SignBoardIndex index;
    private final Function<String, Optional<BoardTarget>> resolve;
    private final Function<OrderFilter, CompletableFuture<Integer>> count;
    private final SignWriter writer;
    private final RefreshSchedule schedule;
    private final Executor mainThread;
    private final Logger logger;
    private final Map<BoardLocation, Integer> shown = new HashMap<>();
    private boolean running;

    /**
     * @param index      the board signs of this server
     * @param resolve    resolves the spec of a sign
     * @param count      counts the open orders of a board
     * @param writer     writes a sign
     * @param schedule   decides when to refresh
     * @param mainThread executor of the main thread
     * @param logger     logger for failed counts
     */
    public SignBoardRefresher(
            SignBoardIndex index,
            Function<String, Optional<BoardTarget>> resolve,
            Function<OrderFilter, CompletableFuture<Integer>> count,
            SignWriter writer,
            RefreshSchedule schedule,
            Executor mainThread,
            Logger logger) {
        this.index = index;
        this.resolve = resolve;
        this.count = count;
        this.writer = writer;
        this.schedule = schedule;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    /**
     * Notes that an order changed. Thread safe.
     */
    public void markDirty() {
        schedule.markDirty();
    }

    /**
     * Writes every sign again on the next tick, for new signs and changed presets.
     */
    public void rerender() {
        shown.clear();
        schedule.force();
    }

    /**
     * Refreshes on the next tick, for a new sign.
     */
    public void refreshSoon() {
        schedule.force();
    }

    /**
     * Forgets what was written to a sign that was broken.
     *
     * @param location position of the sign
     */
    public void forget(BoardLocation location) {
        shown.remove(location);
    }

    /**
     * Starts a refresh when one is due and none is running. Main thread.
     */
    public void tick() {
        if (running || !schedule.due()) return;
        schedule.started();
        running = true;
        refresh().whenComplete((ignored, err) -> {
            if (err != null) logger.log(Level.WARNING, "Could not refresh the board signs.", err);
            mainThread.execute(() -> running = false);
        });
    }

    /**
     * Counts every distinct board of the index and writes the signs whose count changed.
     *
     * @return completes once the signs are written
     */
    public CompletableFuture<Void> refresh() {
        Map<String, BoardTarget> targets = new HashMap<>();
        for (var spec : index.specs()) {
            resolve.apply(spec).ifPresent(target -> targets.put(spec, target));
        }
        Map<String, CompletableFuture<Optional<Integer>>> counts = new HashMap<>();
        targets.forEach((spec, target) -> counts.put(spec, countSafely(spec, target)));
        return CompletableFuture.allOf(counts.values().toArray(CompletableFuture[]::new))
                .thenRunAsync(() -> apply(targets, collect(counts)), mainThread);
    }

    private CompletableFuture<Optional<Integer>> countSafely(String spec, BoardTarget target) {
        return count.apply(target.filter()).thenApply(Optional::of).exceptionally(err -> {
            logger.log(Level.WARNING, "Could not count the open orders of board '" + spec + "'.", err);
            return Optional.empty();
        });
    }

    private static Map<String, Integer> collect(Map<String, CompletableFuture<Optional<Integer>>> counts) {
        Map<String, Integer> collected = new HashMap<>();
        counts.forEach((spec, future) -> future.join().ifPresent(found -> collected.put(spec, found)));
        return collected;
    }

    /**
     * Writes the counts to the signs whose count changed. Main thread.
     *
     * @param targets the boards by spec
     * @param counts  open orders by spec; a spec without a count is left as it is
     */
    void apply(Map<String, BoardTarget> targets, Map<String, Integer> counts) {
        var boards = index.boards();
        shown.keySet().retainAll(boards.keySet());
        boards.forEach((location, spec) -> {
            var target = targets.get(spec);
            var found = counts.get(spec);
            if (target == null || found == null || Objects.equals(shown.get(location), found)) return;
            switch (writer.write(location, target, found)) {
                case WRITTEN -> shown.put(location, found);
                case UNLOADED -> shown.remove(location);
                case GONE -> {
                    index.remove(location);
                    shown.remove(location);
                }
            }
        });
    }

    /**
     * Writes a board sign.
     */
    @FunctionalInterface
    public interface SignWriter {
        /**
         * @param location position of the sign
         * @param target   what the board shows
         * @param count    open orders of the board
         * @return what became of the sign
         */
        WriteResult write(BoardLocation location, BoardTarget target, int count);
    }

    /**
     * What became of a sign that was to be written.
     */
    public enum WriteResult {
        /**
         * The sign shows the count now.
         */
        WRITTEN,
        /**
         * The chunk of the sign is not loaded; the sign is written once it is loaded and counted again.
         */
        UNLOADED,
        /**
         * The block is no board sign any more.
         */
        GONE
    }
}
