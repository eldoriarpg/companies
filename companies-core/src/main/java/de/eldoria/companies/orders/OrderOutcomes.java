/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ItemAmount;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ends orders and records every ending in the history of the side it concerns.
 * <p>
 * Each method makes the conditional state change first and records the entry only when that change succeeded, so an
 * ending that lost a race leaves no entry. A history entry that could not be written is logged and does not undo the
 * ending. Abort and expiry load the deliveries of the order fresh, past the order cache, right before the change, so
 * the company's entry shows what was delivered before the items went back to the workers.
 */
public final class OrderOutcomes {
    private final AOrderData orderData;
    private final HistoryRecorder history;
    private final Logger logger;
    private final Executor executor;

    /**
     * @param orderData order repository
     * @param history   writes the history entries
     * @param logger    logs entries that could not be written
     * @param executor  loads orders off the main thread
     */
    public OrderOutcomes(AOrderData orderData, HistoryRecorder history, Logger logger, Executor executor) {
        this.orderData = orderData;
        this.history = history;
        this.logger = logger;
        this.executor = executor;
    }

    /**
     * Marks a delivered order as received and records {@link HistoryOutcome#RECEIVED} for its owner.
     *
     * @param order   delivered order
     * @param pending items that did not fit into the owner's inventory
     * @return true when the order was delivered and is now received
     */
    public CompletableFuture<Boolean> receive(FullOrder order, List<ItemAmount> pending) {
        return ownerEnding(order, HistoryOutcome.RECEIVED, () -> orderData
                .submitOrderReceive(order, pending)
                .asFuture());
    }

    /**
     * Deletes an unclaimed order its owner cancelled and records {@link HistoryOutcome#CANCELLED} for the owner.
     *
     * @param order unclaimed order
     * @return true when the order was unclaimed and is now deleted
     */
    public CompletableFuture<Boolean> cancel(FullOrder order) {
        return ownerEnding(order, HistoryOutcome.CANCELLED, () -> orderData
                .submitOrderDeletion(order)
                .asFuture());
    }

    /**
     * Removes an order nobody claimed in time and records {@link HistoryOutcome#PURGED} for its owner.
     *
     * @param order unclaimed order
     * @return true when the order was unclaimed and is now removed
     */
    public CompletableFuture<Boolean> purge(FullOrder order) {
        return ownerEnding(order, HistoryOutcome.PURGED, () -> orderData
                .submitOrderPurge(order)
                .asFuture());
    }

    /**
     * Gives a claimed order back to the market for its company and records {@link HistoryOutcome#ABORTED} for the
     * company.
     *
     * @param order claimed order
     * @return the workers who got items returned, or empty when the order was not claimed
     */
    public CompletableFuture<Optional<Set<UUID>>> abort(SimpleOrder order) {
        return companyEnding(order, HistoryOutcome.ABORTED);
    }

    /**
     * Releases a claimed order whose deadline passed and records {@link HistoryOutcome#EXPIRED} for the company.
     *
     * @param order claimed order
     * @return the workers who got items returned, or empty when the order was not claimed
     */
    public CompletableFuture<Optional<Set<UUID>>> expire(SimpleOrder order) {
        return companyEnding(order, HistoryOutcome.EXPIRED);
    }

    private CompletableFuture<Boolean> ownerEnding(
            FullOrder order, HistoryOutcome outcome, Supplier<CompletableFuture<Boolean>> change) {
        return change.get().thenCompose(changed -> {
            if (!Boolean.TRUE.equals(changed)) return CompletableFuture.completedFuture(false);
            return logged(history.ownerEntry(order, outcome), order).thenApply(ignored -> true);
        });
    }

    private CompletableFuture<Optional<Set<UUID>>> companyEnding(SimpleOrder order, HistoryOutcome outcome) {
        var company = order.company();
        return CompletableFuture.supplyAsync(() -> orderData.toFullOrder(order), executor)
                .thenCompose(fullOrder -> orderData
                        .submitUnclaimOrder(order)
                        .asFuture()
                        .thenCompose(workers -> {
                            if (workers == null || workers.isEmpty()) {
                                return CompletableFuture.completedFuture(Optional.<Set<UUID>>empty());
                            }
                            return logged(history.companyEntry(fullOrder, company, outcome, List.of()), order)
                                    .thenApply(ignored -> workers);
                        }));
    }

    private CompletableFuture<Optional<Long>> logged(CompletableFuture<Optional<Long>> entry, SimpleOrder order) {
        return entry.exceptionally(err -> {
            logger.log(Level.SEVERE, "Could not write the history entry of order " + order.id(), err);
            return Optional.empty();
        });
    }
}
