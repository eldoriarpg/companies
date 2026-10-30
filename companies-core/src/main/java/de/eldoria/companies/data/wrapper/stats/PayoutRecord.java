/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.stats;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What one worker got for one completed order. The ledger keeps one record per worker and order; it is the source of
 * member statistics and of the ranked time ranges.
 *
 * @param order   id of the order
 * @param company id of the company that completed the order, 0 for an order a player completed alone
 * @param worker  worker who delivered items
 * @param items   items the worker delivered to the order
 * @param gross   share before tax
 * @param tax     tax taken from the share
 * @param share   part of the share after tax that went to the company treasury
 * @param net        amount paid out of the order's money
 * @param paid       false when the payment was cancelled; such records count items and orders but no money
 * @param created    when the order was completed
 * @param eventExtra what the reward multipliers of server events paid on top, neither taxed nor shared with the
 *                   treasury; 0 without such an event
 */
public record PayoutRecord(
        int order,
        int company,
        UUID worker,
        int items,
        double gross,
        double tax,
        double share,
        double net,
        boolean paid,
        LocalDateTime created,
        double eventExtra) {
    /**
     * Creates a record of a payout without an event extra.
     *
     * @param order   id of the order
     * @param company id of the company that completed the order
     * @param worker  worker who delivered items
     * @param items   items the worker delivered to the order
     * @param gross   share before tax
     * @param tax     tax taken from the share
     * @param share   part of the share after tax that went to the company treasury
     * @param net     amount paid out
     * @param paid    false when the payment was cancelled
     * @param created when the order was completed
     */
    public PayoutRecord(
            int order,
            int company,
            UUID worker,
            int items,
            double gross,
            double tax,
            double share,
            double net,
            boolean paid,
            LocalDateTime created) {
        this(order, company, worker, items, gross, tax, share, net, paid, created, 0.0);
    }

    /**
     * Creates a record of a payout without a treasury share.
     *
     * @param order   id of the order
     * @param company id of the company that completed the order
     * @param worker  worker who delivered items
     * @param items   items the worker delivered to the order
     * @param gross   share before tax
     * @param tax     tax taken from the share
     * @param net     amount paid out
     * @param paid    false when the payment was cancelled
     * @param created when the order was completed
     */
    public PayoutRecord(
            int order,
            int company,
            UUID worker,
            int items,
            double gross,
            double tax,
            double net,
            boolean paid,
            LocalDateTime created) {
        this(order, company, worker, items, gross, tax, 0.0, net, paid, created);
    }

    /**
     * @param extra what server events paid on top
     * @return a copy with the extra
     */
    public PayoutRecord withEventExtra(double extra) {
        return new PayoutRecord(order, company, worker, items, gross, tax, share, net, paid, created, extra);
    }

    /**
     * Whether the worker completed the order alone, without a company.
     *
     * @return true for the payout of a solo order
     */
    public boolean solo() {
        return company <= 0;
    }
}
