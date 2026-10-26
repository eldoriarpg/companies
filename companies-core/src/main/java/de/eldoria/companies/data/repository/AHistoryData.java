/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * The order history of owners and companies.
 * <p>
 * Each entry belongs to one side of an order; owner and company reads never return entries of the other side. Only
 * {@link #retrieveByOrder(int)} returns both sides and is meant for admins. Times are bound from Java, never taken from
 * the database clock. All futures complete on the executor given to the constructor.
 */
public abstract class AHistoryData {
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    /**
     * @param mapper   mapper for snapshots, able to read and write item stacks
     * @param executor executor of the futures
     */
    protected AHistoryData(ObjectMapper mapper, ExecutorService executor) {
        this.mapper = mapper;
        this.executor = executor;
    }

    /**
     * Stores an entry.
     *
     * @param entry entry to store; its id is ignored
     * @return id of the stored entry, or empty when it could not be stored
     */
    public CompletableFuture<Optional<Long>> submitEntry(HistoryEntry entry) {
        return CompletableFuture.supplyAsync(() -> saveEntry(entry), executor);
    }

    protected abstract Optional<Long> saveEntry(HistoryEntry entry);

    /**
     * One page of an owner's history, the latest first.
     *
     * @param owner owner of the orders
     * @param page  page, starting at 1
     * @param size  entries per page
     * @return owner entries of the page
     */
    public CompletableFuture<List<HistoryEntry>> retrieveOwnerHistory(UUID owner, int page, int size) {
        return CompletableFuture.supplyAsync(() -> getOwnerHistory(owner, page, size), executor);
    }

    protected abstract List<HistoryEntry> getOwnerHistory(UUID owner, int page, int size);

    /**
     * One page of a company's history, the latest first.
     *
     * @param company id of the company
     * @param page    page, starting at 1
     * @param size    entries per page
     * @return company entries of the page
     */
    public CompletableFuture<List<HistoryEntry>> retrieveCompanyHistory(int company, int page, int size) {
        return CompletableFuture.supplyAsync(() -> getCompanyHistory(company, page, size), executor);
    }

    protected abstract List<HistoryEntry> getCompanyHistory(int company, int page, int size);

    /**
     * One entry. Callers check that it belongs to the side asking for it.
     *
     * @param id id of the entry
     * @return the entry, or empty when there is none
     */
    public CompletableFuture<Optional<HistoryEntry>> retrieveEntry(long id) {
        return CompletableFuture.supplyAsync(() -> getEntry(id), executor);
    }

    protected abstract Optional<HistoryEntry> getEntry(long id);

    /**
     * Every entry of an order, of both sides, oldest first. For admins only.
     *
     * @param order id of the order
     * @return entries
     */
    public CompletableFuture<List<HistoryEntry>> retrieveByOrder(int order) {
        return CompletableFuture.supplyAsync(() -> getByOrder(order), executor);
    }

    protected abstract List<HistoryEntry> getByOrder(int order);

    /**
     * The company entries of one outcome that ended in a span of time, such as the orders delivered on one day. Orders
     * the server placed are left out, so they never become market data.
     *
     * @param outcome the outcome
     * @param from    the start of the span, included
     * @param to      the end of the span, excluded
     * @return the entries, oldest first
     */
    public CompletableFuture<List<HistoryEntry>> retrieveCompanyEntries(
            HistoryOutcome outcome, LocalDateTime from, LocalDateTime to) {
        return CompletableFuture.supplyAsync(() -> getCompanyEntries(outcome, from, to), executor);
    }

    protected abstract List<HistoryEntry> getCompanyEntries(
            HistoryOutcome outcome, LocalDateTime from, LocalDateTime to);

    /**
     * Deletes every entry of orders that ended before a point in time, and the decisions of system orders placed before
     * it whose order is gone.
     *
     * @param before entries that ended before this are deleted
     * @return number of entries deleted
     */
    public CompletableFuture<Integer> submitPurge(LocalDateTime before) {
        return CompletableFuture.supplyAsync(() -> purgeBefore(before), executor);
    }

    protected abstract int purgeBefore(LocalDateTime before);

    /**
     * Writes a snapshot for storage.
     *
     * @param snapshot snapshot
     * @return JSON object
     */
    protected String toJson(HistorySnapshot snapshot) {
        return snapshot.toJson(mapper);
    }

    /**
     * Reads a stored snapshot.
     *
     * @param json stored snapshot
     * @return snapshot
     * @throws SQLException when the text is no snapshot
     */
    protected HistorySnapshot toSnapshot(String json) throws SQLException {
        try {
            return HistorySnapshot.fromJson(mapper, json);
        } catch (JacksonException e) {
            throw new SQLException("Could not read history snapshot", e);
        }
    }
}
