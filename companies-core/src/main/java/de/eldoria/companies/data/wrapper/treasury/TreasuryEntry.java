/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.treasury;

import de.eldoria.companies.components.company.TreasuryKind;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What a treasury transaction is about, written to the treasury log with the amount and the new balance.
 *
 * @param kind    what moves the money
 * @param actor   member or admin who caused it, or null for payout shares and system actions
 * @param order   the company's own order for payout shares, otherwise null
 * @param note    free text such as the new name of a rename, or null
 * @param created current time
 */
public record TreasuryEntry(
        TreasuryKind kind,
        @Nullable UUID actor,
        @Nullable Integer order,
        @Nullable String note,
        LocalDateTime created) {
    /**
     * An entry caused by a player without order or note.
     *
     * @param kind    what moves the money
     * @param actor   member or admin
     * @param created current time
     * @return entry
     */
    public static TreasuryEntry by(TreasuryKind kind, UUID actor, LocalDateTime created) {
        return new TreasuryEntry(kind, actor, null, null, created);
    }
}
