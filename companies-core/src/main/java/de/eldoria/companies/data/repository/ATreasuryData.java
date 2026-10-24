/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.treasury.Treasury;
import de.eldoria.companies.data.wrapper.treasury.TreasuryEntry;
import de.eldoria.companies.data.wrapper.treasury.TreasuryLogEntry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * The company treasuries and their log.
 * <p>
 * Money is stored in whole cents. A treasury row is created on the first credit or share change; a company without a
 * row has a balance and share of 0. Every change of the balance runs in one transaction with its log entry, and a
 * debit only happens when the balance covers it, so two servers can never pay out the same money. Times are bound
 * from Java. All futures complete on the executor given to the constructor.
 */
public abstract class ATreasuryData {
    private final ExecutorService executor;

    protected ATreasuryData(ExecutorService executor) {
        this.executor = executor;
    }

    /**
     * The treasury of a company.
     *
     * @param company id of the company
     * @return balance and share, zeros when the company never used its treasury
     */
    public CompletableFuture<Treasury> retrieveTreasury(int company) {
        return CompletableFuture.supplyAsync(() -> getTreasury(company), executor);
    }

    protected abstract Treasury getTreasury(int company);

    /**
     * Adds money to a treasury and logs it.
     *
     * @param company id of the company
     * @param cents   amount in cents, more than 0
     * @param entry   what the money is for
     * @return the new balance, or empty when nothing was stored
     */
    public CompletableFuture<Optional<Long>> submitCredit(int company, long cents, TreasuryEntry entry) {
        if (cents <= 0) return CompletableFuture.completedFuture(Optional.empty());
        return CompletableFuture.supplyAsync(() -> credit(company, cents, entry), executor);
    }

    protected abstract Optional<Long> credit(int company, long cents, TreasuryEntry entry);

    /**
     * Takes money from a treasury and logs it, only when the balance covers the amount.
     *
     * @param company id of the company
     * @param cents   amount in cents, more than 0
     * @param entry   what the money is for
     * @return the new balance, or empty when the balance is too low or nothing was stored
     */
    public CompletableFuture<Optional<Long>> submitDebit(int company, long cents, TreasuryEntry entry) {
        if (cents <= 0) return CompletableFuture.completedFuture(Optional.empty());
        return CompletableFuture.supplyAsync(() -> debit(company, cents, entry), executor);
    }

    protected abstract Optional<Long> debit(int company, long cents, TreasuryEntry entry);

    /**
     * Sets the share of payouts that goes to the treasury and logs the change with the new percent as note.
     *
     * @param company id of the company
     * @param percent share in percent
     * @param actor   member who changed it
     * @param now     current time
     * @return true when the share was stored
     */
    public CompletableFuture<Boolean> submitShare(int company, int percent, UUID actor, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> saveShare(company, percent, actor, now), executor);
    }

    protected abstract boolean saveShare(int company, int percent, UUID actor, LocalDateTime now);

    /**
     * Empties a treasury, because its company is disbanded or deleted, and logs the payout.
     *
     * @param company id of the company
     * @param entry   what the money is for, usually {@link de.eldoria.companies.components.company.TreasuryKind#DISBAND}
     * @return the balance before, 0 when it was empty, or empty when nothing was changed
     */
    public CompletableFuture<Optional<Long>> submitDrain(int company, TreasuryEntry entry) {
        return CompletableFuture.supplyAsync(() -> drain(company, entry), executor);
    }

    protected abstract Optional<Long> drain(int company, TreasuryEntry entry);

    /**
     * One page of a company's treasury log, newest first.
     *
     * @param company id of the company
     * @param page    page, starting at 1
     * @param size    entries per page
     * @return entries of the page
     */
    public CompletableFuture<List<TreasuryLogEntry>> retrieveLog(int company, int page, int size) {
        return CompletableFuture.supplyAsync(() -> getLog(company, page, size), executor);
    }

    protected abstract List<TreasuryLogEntry> getLog(int company, int page, int size);

    /**
     * Deletes every log entry written before a point in time.
     *
     * @param before entries older than this are deleted
     * @return number of entries deleted
     */
    public CompletableFuture<Integer> submitLogPurge(LocalDateTime before) {
        return CompletableFuture.supplyAsync(() -> purgeLogBefore(before), executor);
    }

    protected abstract int purgeLogBefore(LocalDateTime before);
}
