/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.data.repository.AHistoryData;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.stats.PayoutRecord;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the history entries of ended orders, one per side.
 * <p>
 * An owner entry keeps the lines, the price and the fee. A company entry keeps the lines with what was delivered, the
 * money of the paid payouts and what every worker delivered and got; payouts whose payment was cancelled count items
 * but no money. Without payouts, as for aborted or expired orders, the workers are taken from the deliveries of the
 * order with no money. Callers record an entry only after the state change of the order succeeded.
 */
public final class HistoryRecorder {
    private final AHistoryData historyData;
    private final Clock clock;

    /**
     * @param historyData history repository
     * @param clock       clock giving the end time of entries
     */
    public HistoryRecorder(AHistoryData historyData, Clock clock) {
        this.historyData = historyData;
        this.clock = clock;
    }

    /**
     * Records how an order ended for its owner.
     *
     * @param order   order as it was before it ended
     * @param outcome owner outcome
     * @return id of the entry, or empty when it could not be stored
     * @throws IllegalArgumentException when the outcome is no owner outcome
     */
    public CompletableFuture<Optional<Long>> ownerEntry(FullOrder order, HistoryOutcome outcome) {
        var entry = HistoryEntry.ofOwner(
                order.id(),
                order.owner(),
                outcome,
                order.name(),
                order.created(),
                LocalDateTime.now(clock),
                ownerSnapshot(order));
        return historyData.submitEntry(entry);
    }

    /**
     * Records how an order ended for the company that worked on it.
     *
     * @param order   order as it was before it ended
     * @param company id of the company
     * @param outcome company outcome
     * @param payouts the payouts of the order, empty when nothing was paid
     * @return id of the entry, or empty when it could not be stored
     * @throws IllegalArgumentException when the outcome is no company outcome
     */
    public CompletableFuture<Optional<Long>> companyEntry(
            FullOrder order, int company, HistoryOutcome outcome, List<PayoutRecord> payouts) {
        var entry = HistoryEntry.ofCompany(
                order.id(),
                company,
                outcome,
                order.name(),
                order.created(),
                LocalDateTime.now(clock),
                companySnapshot(order, payouts));
        return historyData.submitEntry(entry);
    }

    /**
     * The owner's view of an order: lines, price and fee.
     *
     * @param order order
     * @return snapshot without worker money
     */
    static HistorySnapshot ownerSnapshot(FullOrder order) {
        return new HistorySnapshot(lines(order), order.price(), order.fee(), 0, 0, 0, List.of());
    }

    /**
     * The company's view of an order: lines, price, the paid money and the workers.
     *
     * @param order   order
     * @param payouts payouts of the order
     * @return snapshot without fee
     */
    static HistorySnapshot companySnapshot(FullOrder order, List<PayoutRecord> payouts) {
        var gross = 0.0;
        var tax = 0.0;
        var net = 0.0;
        for (var payout : payouts) {
            if (!payout.paid()) continue;
            gross += payout.gross();
            tax += payout.tax();
            net += payout.net();
        }
        var workers = payouts.isEmpty() ? deliveringWorkers(order) : paidWorkers(payouts);
        return new HistorySnapshot(lines(order), order.price(), 0, gross, tax, net, workers);
    }

    private static List<HistorySnapshot.Line> lines(FullOrder order) {
        List<HistorySnapshot.Line> lines = new ArrayList<>();
        for (var content : order.contents()) {
            lines.add(new HistorySnapshot.Line(
                    content.stack(), content.exact(), content.amount(), content.delivered(), content.price()));
        }
        return lines;
    }

    private static List<HistorySnapshot.Worker> paidWorkers(List<PayoutRecord> payouts) {
        Map<UUID, HistorySnapshot.Worker> workers = new LinkedHashMap<>();
        for (var payout : payouts) {
            var gross = payout.paid() ? payout.gross() : 0;
            var net = payout.paid() ? payout.net() : 0;
            workers.merge(
                    payout.worker(),
                    new HistorySnapshot.Worker(payout.worker(), payout.items(), gross, net),
                    (left, right) -> new HistorySnapshot.Worker(
                            left.worker(),
                            left.items() + right.items(),
                            left.gross() + right.gross(),
                            left.net() + right.net()));
        }
        return List.copyOf(workers.values());
    }

    private static List<HistorySnapshot.Worker> deliveringWorkers(FullOrder order) {
        Map<UUID, Integer> items = new LinkedHashMap<>();
        for (var content : order.contents()) {
            content.workerAmount().forEach((worker, amount) -> items.merge(worker, amount, Integer::sum));
        }
        return items.entrySet().stream()
                .map(entry -> new HistorySnapshot.Worker(entry.getKey(), entry.getValue(), 0, 0))
                .toList();
    }
}
