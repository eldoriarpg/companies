/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.treasury;

import de.eldoria.companies.components.company.TreasuryKind;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * One transaction in the treasury log.
 *
 * @param id      id of the entry
 * @param company id of the company
 * @param kind    what moved the money
 * @param amount  signed cents: positive into the treasury, negative out of it, 0 for share changes
 * @param balance balance in cents after the transaction
 * @param actor   member or admin who caused it, or null for payout shares and system actions
 * @param order   the company's own order for payout shares, otherwise null
 * @param note    free text such as the new name of a rename or the new share, or null
 * @param created when the transaction happened
 */
public record TreasuryLogEntry(
        long id,
        int company,
        TreasuryKind kind,
        long amount,
        long balance,
        @Nullable UUID actor,
        @Nullable Integer order,
        @Nullable String note,
        LocalDateTime created) {
    /**
     * @return the member or admin who caused the transaction, or empty for payout shares and system actions
     */
    public Optional<UUID> actorId() {
        return Optional.ofNullable(actor);
    }

    /**
     * @return the company's own order of a payout share, or empty
     */
    public Optional<Integer> orderId() {
        return Optional.ofNullable(order);
    }
}
