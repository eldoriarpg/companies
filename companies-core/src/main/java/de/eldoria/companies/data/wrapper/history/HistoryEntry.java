/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.history;

import de.eldoria.companies.components.order.HistoryOutcome;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One side's record of an ended order. An entry belongs either to the owner or to the company, never to both, so it
 * cannot name the other side.
 *
 * @param id       id of the entry, 0 before it is stored
 * @param order    id of the order
 * @param owner    owner of the order on owner entries, otherwise null
 * @param company  id of the company on company entries, otherwise null
 * @param outcome  how the order ended
 * @param name     name of the order
 * @param placed   when the order was placed
 * @param finished when the order ended
 * @param snapshot what the order looked like
 */
public record HistoryEntry(
        long id,
        int order,
        @Nullable UUID owner,
        @Nullable Integer company,
        HistoryOutcome outcome,
        String name,
        LocalDateTime placed,
        LocalDateTime finished,
        HistorySnapshot snapshot) {
    /**
     * @throws IllegalArgumentException when the entry has both or no side, or the outcome does not fit its side
     */
    public HistoryEntry {
        if ((owner == null) == (company == null)) {
            throw new IllegalArgumentException("A history entry belongs to exactly one side.");
        }
        if (owner != null ? !outcome.ownerSide() : !outcome.companySide()) {
            throw new IllegalArgumentException(outcome + " is not an outcome of this side.");
        }
    }

    /**
     * A new entry of the order's owner.
     *
     * @param order    id of the order
     * @param owner    owner of the order
     * @param outcome  how the order ended for the owner
     * @param name     name of the order
     * @param placed   when the order was placed
     * @param finished when the order ended
     * @param snapshot owner snapshot
     * @return entry without id
     */
    public static HistoryEntry ofOwner(
            int order,
            UUID owner,
            HistoryOutcome outcome,
            String name,
            LocalDateTime placed,
            LocalDateTime finished,
            HistorySnapshot snapshot) {
        return new HistoryEntry(
                0, order, Objects.requireNonNull(owner), null, outcome, name, placed, finished, snapshot);
    }

    /**
     * A new entry of the company that worked on the order.
     *
     * @param order    id of the order
     * @param company  id of the company
     * @param outcome  how the order ended for the company
     * @param name     name of the order
     * @param placed   when the order was placed
     * @param finished when the order ended
     * @param snapshot company snapshot
     * @return entry without id
     */
    public static HistoryEntry ofCompany(
            int order,
            int company,
            HistoryOutcome outcome,
            String name,
            LocalDateTime placed,
            LocalDateTime finished,
            HistorySnapshot snapshot) {
        return new HistoryEntry(0, order, null, company, outcome, name, placed, finished, snapshot);
    }

    /**
     * @return owner of the order on owner entries
     */
    public Optional<UUID> ownerSide() {
        return Optional.ofNullable(owner);
    }

    /**
     * @return id of the company on company entries
     */
    public Optional<Integer> companySide() {
        return Optional.ofNullable(company);
    }
}
