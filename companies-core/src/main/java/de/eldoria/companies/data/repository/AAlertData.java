/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.alert.AlertResult;
import de.eldoria.companies.data.wrapper.alert.HitResult;
import de.eldoria.companies.data.wrapper.alert.OrderAlert;
import de.eldoria.companies.orders.query.OrderFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * The order alerts of the companies.
 * <p>
 * The limit of alerts per company is checked in the transaction that saves an alert, with the company row locked, so
 * two servers cannot exceed it. The rate limit of an alert is a conditional update of its last notification, so it
 * holds across servers as well. Times are bound from Java. All futures complete on the executor given to the
 * constructor.
 */
public abstract class AAlertData {
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    /**
     * Creates the repository.
     *
     * @param mapper   mapper for the filters
     * @param executor executor for futures
     */
    protected AAlertData(ObjectMapper mapper, ExecutorService executor) {
        this.mapper = mapper;
        this.executor = executor;
    }

    /**
     * Saves an alert unless the company has one with this name or reached its limit.
     *
     * @param company   id of the company
     * @param name      name of the alert
     * @param filter    orders the alert matches
     * @param creator   member who saves it
     * @param maxAlerts alerts the company may have
     * @param now       current time
     * @return the outcome
     */
    public CompletableFuture<AlertResult> submitAlert(
            int company, String name, OrderFilter filter, UUID creator, int maxAlerts, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> saveAlert(company, name, filter, creator, maxAlerts, now), executor);
    }

    protected abstract AlertResult saveAlert(
            int company, String name, OrderFilter filter, UUID creator, int maxAlerts, LocalDateTime now);

    /**
     * The alerts of a company by name.
     *
     * @param company id of the company
     * @return alerts of the company
     */
    public CompletableFuture<List<OrderAlert>> retrieveByCompany(int company) {
        return CompletableFuture.supplyAsync(() -> getByCompany(company), executor);
    }

    protected abstract List<OrderAlert> getByCompany(int company);

    /**
     * The enabled alerts of all companies, to match a new order against.
     *
     * @return enabled alerts
     */
    public CompletableFuture<List<OrderAlert>> retrieveEnabled() {
        return CompletableFuture.supplyAsync(this::getEnabled, executor);
    }

    protected abstract List<OrderAlert> getEnabled();

    /**
     * Deletes an alert.
     *
     * @param company id of the company
     * @param name    name of the alert
     * @return true when the alert existed
     */
    public CompletableFuture<Boolean> submitRemoval(int company, String name) {
        return CompletableFuture.supplyAsync(() -> remove(company, name), executor);
    }

    protected abstract boolean remove(int company, String name);

    /**
     * Turns an alert on or off.
     *
     * @param company id of the company
     * @param name    name of the alert
     * @param enabled whether the alert notifies
     * @return true when the alert existed
     */
    public CompletableFuture<Boolean> submitEnabled(int company, String name, boolean enabled) {
        return CompletableFuture.supplyAsync(() -> saveEnabled(company, name, enabled), executor);
    }

    protected abstract boolean saveEnabled(int company, String name, boolean enabled);

    /**
     * Records that a new order matches an alert. The alert notifies when its last notification is at least one window
     * ago, which also clears the matches held back so far; otherwise the match is held back for the digest.
     *
     * @param alert  id of the alert
     * @param now    current time
     * @param window shortest time between two notifications of the alert
     * @return whether to notify now
     */
    public CompletableFuture<HitResult> submitHit(int alert, LocalDateTime now, Duration window) {
        return CompletableFuture.supplyAsync(() -> hit(alert, now, now.minus(window)), executor);
    }

    protected abstract HitResult hit(int alert, LocalDateTime now, LocalDateTime notifiedBefore);

    /**
     * The enabled alerts with held back matches whose window has passed.
     *
     * @param now    current time
     * @param window shortest time between two notifications of an alert
     * @return alerts due for a digest
     */
    public CompletableFuture<List<OrderAlert>> retrieveDigestDue(LocalDateTime now, Duration window) {
        return CompletableFuture.supplyAsync(() -> getDigestDue(now.minus(window)), executor);
    }

    protected abstract List<OrderAlert> getDigestDue(LocalDateTime notifiedBefore);

    /**
     * Claims the held back matches of an alert for a digest and starts a new window. Only one caller can claim them,
     * because the update requires the count the caller read.
     *
     * @param alert alert as read by {@link #retrieveDigestDue(LocalDateTime, Duration)}
     * @param now   current time
     * @return number of matches claimed, 0 when another caller claimed them
     */
    public CompletableFuture<Integer> submitDigestClaim(OrderAlert alert, LocalDateTime now) {
        if (alert.pending() <= 0) return CompletableFuture.completedFuture(0);
        return CompletableFuture.supplyAsync(() -> claimDigest(alert, now) ? alert.pending() : 0, executor);
    }

    protected abstract boolean claimDigest(OrderAlert alert, LocalDateTime now);

    /**
     * Writes a filter for storage.
     *
     * @param filter filter
     * @return JSON object
     */
    protected String toJson(OrderFilter filter) {
        return mapper.writeValueAsString(filter);
    }

    /**
     * Reads a stored filter.
     *
     * @param json stored filter
     * @return filter
     * @throws SQLException when the text holds no filter
     */
    protected OrderFilter toFilter(String json) throws SQLException {
        try {
            return mapper.readValue(json, OrderFilter.class);
        } catch (JacksonException e) {
            throw new SQLException("Could not read the filter of an alert", e);
        }
    }
}
