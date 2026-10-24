/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/**
 * How an order ended, as recorded in the order history.
 * <p>
 * Every history entry belongs to one side of the order: the owner or the company that worked on it. An entry never
 * names the other side, which keeps orders anonymous.
 */
public enum HistoryOutcome implements Translatable {
    /**
     * The owner received the delivered items. Owner side.
     */
    RECEIVED(1, true, false),
    /**
     * The owner cancelled the unclaimed order. Owner side.
     */
    CANCELLED(2, true, false),
    /**
     * The order was removed after it was not claimed for too long. Owner side.
     */
    PURGED(3, true, false),
    /**
     * An admin cancelled the order. Owner side, and company side when it was claimed.
     */
    CANCELLED_BY_ADMIN(4, true, true),
    /**
     * The company delivered every item. Company side.
     */
    DELIVERED(5, false, true),
    /**
     * The company gave the order up. Company side.
     */
    ABORTED(6, false, true),
    /**
     * The company did not deliver in time. Company side.
     */
    EXPIRED(7, false, true),
    /**
     * An admin took the order away from the company. Company side.
     */
    UNCLAIMED_BY_ADMIN(8, false, true);

    private final int id;
    private final boolean ownerSide;
    private final boolean companySide;

    HistoryOutcome(int id, boolean ownerSide, boolean companySide) {
        this.id = id;
        this.ownerSide = ownerSide;
        this.companySide = companySide;
    }

    /**
     * The outcome with the given id.
     *
     * @param id stored id
     * @return outcome, or empty when no outcome has this id
     */
    public static Optional<HistoryOutcome> byId(int id) {
        for (var outcome : values()) if (outcome.id == id) return Optional.of(outcome);
        return Optional.empty();
    }

    /**
     * The stable id of this outcome, as stored in the database.
     *
     * @return id
     */
    public int id() {
        return id;
    }

    /**
     * Whether entries of the order's owner can have this outcome.
     *
     * @return true for owner outcomes
     */
    public boolean ownerSide() {
        return ownerSide;
    }

    /**
     * Whether entries of the company that worked on the order can have this outcome.
     *
     * @return true for company outcomes
     */
    public boolean companySide() {
        return companySide;
    }

    @Override
    public @NotNull String translationKey() {
        return "enums.historyOutcome." + name().toLowerCase(Locale.ROOT);
    }
}
