/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.series.OrderSeries;
import de.eldoria.companies.data.wrapper.series.SeriesLine;
import de.eldoria.companies.data.wrapper.series.SeriesState;
import de.eldoria.companies.data.wrapper.series.SeriesStopReason;
import de.eldoria.companies.data.wrapper.series.SeriesWait;
import org.jetbrains.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Repeating orders.
 * <p>
 * Every change of the schedule or the state is a conditional update, so two servers never post the same copy and a
 * stop or resume takes effect once. Copies are normal orders whose {@code series_id} names the series. Times are bound
 * from Java. All futures complete on the executor given to the constructor.
 */
public abstract class ASeriesData {
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    /**
     * Creates the repository.
     *
     * @param mapper   mapper for the lines, able to read and write item stacks
     * @param executor executor for futures
     */
    protected ASeriesData(ObjectMapper mapper, ExecutorService executor) {
        this.mapper = mapper;
        this.executor = executor;
    }

    /**
     * Saves a new series.
     *
     * @param draft series to save; its id is ignored
     * @return the saved series with its id, or empty when it could not be saved
     */
    public CompletableFuture<Optional<OrderSeries>> submitSeries(OrderSeries draft) {
        return CompletableFuture.supplyAsync(() -> saveSeries(draft), executor);
    }

    protected abstract Optional<OrderSeries> saveSeries(OrderSeries draft);

    /**
     * A series by id.
     *
     * @param id id of the series
     * @return the series, or empty when it does not exist
     */
    public CompletableFuture<Optional<OrderSeries>> retrieveSeries(int id) {
        return CompletableFuture.supplyAsync(() -> getSeries(id), executor);
    }

    protected abstract Optional<OrderSeries> getSeries(int id);

    /**
     * The series of a player, newest first.
     *
     * @param owner      owner of the series
     * @param activeOnly true to leave out stopped and finished series
     * @return series of the player
     */
    public CompletableFuture<List<OrderSeries>> retrieveByOwner(UUID owner, boolean activeOnly) {
        return CompletableFuture.supplyAsync(() -> getByOwner(owner, activeOnly), executor);
    }

    protected abstract List<OrderSeries> getByOwner(UUID owner, boolean activeOnly);

    /**
     * Counts the active series of a player.
     *
     * @param owner owner of the series
     * @return number of active series
     */
    public CompletableFuture<Integer> retrieveActiveCount(UUID owner) {
        return CompletableFuture.supplyAsync(() -> countActive(owner), executor);
    }

    protected abstract int countActive(UUID owner);

    /**
     * The active series whose next copy is due, the longest due first.
     *
     * @param now   current time
     * @param limit most series to return
     * @return due series
     */
    public CompletableFuture<List<OrderSeries>> retrieveDue(LocalDateTime now, int limit) {
        return CompletableFuture.supplyAsync(() -> getDue(now, limit), executor);
    }

    protected abstract List<OrderSeries> getDue(LocalDateTime now, int limit);

    /**
     * Claims the due copy of a series: moves the schedule on, counts the copy and clears a waiting reason. Only one
     * caller can claim a copy, because the update requires the series to be unchanged since it was read.
     *
     * @param series  series as read by {@link #retrieveDue(LocalDateTime, int)}
     * @param nextDue when the following copy is due
     * @param now     current time
     * @return true when this call claimed the copy
     */
    public CompletableFuture<Boolean> submitClaim(OrderSeries series, LocalDateTime nextDue, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> claim(series, nextDue, now), executor);
    }

    protected abstract boolean claim(OrderSeries series, LocalDateTime nextDue, LocalDateTime now);

    /**
     * Undoes a claim after the copy could not be saved: restores the schedule and the count of the series as it was
     * before the claim.
     *
     * @param series series as it was passed to {@link #submitClaim(OrderSeries, LocalDateTime, LocalDateTime)}
     * @param now    current time
     * @return true when the claim was undone
     */
    public CompletableFuture<Boolean> submitClaimRelease(OrderSeries series, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> releaseClaim(series, now), executor);
    }

    protected abstract boolean releaseClaim(OrderSeries series, LocalDateTime now);

    /**
     * Records the copy that was just posted: sets it as current order of the series and marks the order as a copy of
     * the series, in one transaction.
     *
     * @param series id of the series
     * @param order  id of the posted order
     * @return true when both were stored
     */
    public CompletableFuture<Boolean> submitCurrentOrder(int series, int order) {
        return CompletableFuture.supplyAsync(() -> saveCurrentOrder(series, order), executor);
    }

    protected abstract boolean saveCurrentOrder(int series, int order);

    /**
     * Marks an active series as waiting.
     *
     * @param series id of the series
     * @param wait   why the due copy is held back
     * @param now    current time
     * @return true only when the series was not already waiting for this reason, so the owner is told once
     */
    public CompletableFuture<Boolean> submitWaiting(int series, SeriesWait wait, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> markWaiting(series, wait, now), executor);
    }

    protected abstract boolean markWaiting(int series, SeriesWait wait, LocalDateTime now);

    /**
     * Ends an active series.
     *
     * @param series id of the series
     * @param state  {@link SeriesState#STOPPED} or {@link SeriesState#FINISHED}
     * @param reason why it was stopped, null when it finished
     * @param now    current time
     * @return true when this call ended it; false when it was not active
     */
    public CompletableFuture<Boolean> submitStop(
            int series, SeriesState state, @Nullable SeriesStopReason reason, LocalDateTime now) {
        if (state == SeriesState.ACTIVE) throw new IllegalArgumentException("A series is stopped or finished");
        return CompletableFuture.supplyAsync(() -> stop(series, state, reason, now), executor);
    }

    protected abstract boolean stop(
            int series, SeriesState state, @Nullable SeriesStopReason reason, LocalDateTime now);

    /**
     * Activates a stopped series again.
     *
     * @param series  id of the series
     * @param nextDue when the next copy is due
     * @param now     current time
     * @return true when this call resumed it; false when it was not stopped
     */
    public CompletableFuture<Boolean> submitResume(int series, LocalDateTime nextDue, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> resume(series, nextDue, now), executor);
    }

    protected abstract boolean resume(int series, LocalDateTime nextDue, LocalDateTime now);

    /**
     * Stores the order limit of a player on all their series, because it can only be read while they are online.
     *
     * @param owner owner of the series
     * @param limit current order limit
     * @return number of series updated
     */
    public CompletableFuture<Integer> submitOrderLimit(UUID owner, int limit) {
        return CompletableFuture.supplyAsync(() -> saveOrderLimit(owner, limit), executor);
    }

    protected abstract int saveOrderLimit(UUID owner, int limit);

    /**
     * The state of the last posted copy of a series.
     *
     * @param series series
     * @return state of the copy, or empty when no copy was posted or it is gone (cancelled or purged)
     */
    public CompletableFuture<Optional<OrderState>> retrieveCurrentOrderState(OrderSeries series) {
        if (series.currentOrder().isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        var order = series.currentOrder().getAsInt();
        return CompletableFuture.supplyAsync(() -> getOrderState(order), executor);
    }

    protected abstract Optional<OrderState> getOrderState(int order);

    /**
     * Writes the lines of a series for storage.
     *
     * @param lines lines
     * @return JSON array
     */
    protected String toJson(List<SeriesLine> lines) {
        return mapper.writeValueAsString(lines.toArray(SeriesLine[]::new));
    }

    /**
     * Reads stored lines.
     *
     * @param json stored lines
     * @return lines
     * @throws SQLException when the text holds no lines
     */
    protected List<SeriesLine> toLines(String json) throws SQLException {
        try {
            return Arrays.asList(mapper.readValue(json, SeriesLine[].class));
        } catch (JacksonException e) {
            throw new SQLException("Could not read the lines of a repeating order", e);
        }
    }
}
