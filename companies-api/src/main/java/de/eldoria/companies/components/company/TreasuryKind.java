/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/**
 * What moved money into or out of a company treasury.
 */
public enum TreasuryKind implements Translatable {
    /**
     * A member paid money in.
     */
    DEPOSIT(0),
    /**
     * A member took money out.
     */
    WITHDRAW(1),
    /**
     * The company's share of a worker's payout for a completed order.
     */
    PAYOUT_SHARE(2),
    /**
     * The owner paid a rename of the company from the treasury.
     */
    RENAME(3),
    /**
     * Money given back after a failed payment, such as a withdrawal the economy refused.
     */
    REFUND(4),
    /**
     * The company was disbanded or deleted and its balance was paid to the owner.
     */
    DISBAND(5),
    /**
     * An admin added money.
     */
    ADMIN_GIVE(6),
    /**
     * An admin removed money.
     */
    ADMIN_TAKE(7),
    /**
     * A member changed the payout share. No money moves.
     */
    SHARE_CHANGED(8);

    private final int id;

    TreasuryKind(int id) {
        this.id = id;
    }

    /**
     * The kind with the given id.
     *
     * @param id stored id
     * @return kind, or empty when no kind has this id
     */
    public static Optional<TreasuryKind> byId(int id) {
        for (var kind : values()) if (kind.id == id) return Optional.of(kind);
        return Optional.empty();
    }

    /**
     * The stable id of this kind, as stored in the database.
     *
     * @return id
     */
    public int id() {
        return id;
    }

    @Override
    public @NotNull String translationKey() {
        return "enums.treasuryKind." + name().toLowerCase(Locale.ROOT);
    }
}
