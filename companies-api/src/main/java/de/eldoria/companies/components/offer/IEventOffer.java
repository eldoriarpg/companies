/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.offer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * An offer the server makes: a shared goal or a contest, paid from an economy account or minted, within a budget.
 * All times are UTC.
 */
public interface IEventOffer {
    /**
     * The id of the offer.
     *
     * @return id
     */
    int id();

    /**
     * Whether it is a goal or a contest.
     *
     * @return kind
     */
    EventOfferKind kind();

    /**
     * The name shown to players.
     *
     * @return name
     */
    String name();

    /**
     * Where it is in its life.
     *
     * @return state
     */
    EventOfferState state();

    /**
     * The server event it runs with, starting, ending and being cancelled with it.
     *
     * @return id of the server event, empty for an offer on its own
     */
    OptionalInt event();

    /**
     * What it asks for, as admins see it: a material name, criteria in their text form or the name of an exact item.
     *
     * @return the item
     */
    String item();

    /**
     * The items asked for in all.
     *
     * @return amount
     */
    int amount();

    /**
     * The items delivered so far.
     *
     * @return delivered items
     */
    int delivered();

    /**
     * The price paid per item.
     *
     * @return unit price
     */
    double unitPrice();

    /**
     * What the whole offer may spend, a contest's prizes included.
     *
     * @return budget
     */
    double budget();

    /**
     * What it spent: for a goal what its chunks hold, for a contest what was paid for deliveries and prizes.
     *
     * @return spent money
     */
    double spent();

    /**
     * The prizes of a contest's first places, first place first.
     *
     * @return prizes, empty for a goal
     */
    List<Double> prizes();

    /**
     * When it starts.
     *
     * @return start in UTC
     */
    LocalDateTime starts();

    /**
     * When it ends.
     *
     * @return end in UTC
     */
    LocalDateTime ends();

    /**
     * When it closed.
     *
     * @return the time in UTC, empty while it runs
     */
    Optional<LocalDateTime> closedAt();
}
