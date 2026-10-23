/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.stats;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What one worker got for one completed order. The ledger keeps one record per worker and order; it is the source of
 * member statistics and of the ranked time ranges.
 *
 * @param order   id of the order
 * @param company id of the company that completed the order
 * @param worker  worker who delivered items
 * @param items   items the worker delivered to the order
 * @param gross   share before tax
 * @param tax     tax taken from the share
 * @param net     amount paid out
 * @param paid    false when the payment was cancelled; such records count items and orders but no money
 * @param created when the order was completed
 */
public record PayoutRecord(
        int order,
        int company,
        UUID worker,
        int items,
        double gross,
        double tax,
        double net,
        boolean paid,
        LocalDateTime created) {}
