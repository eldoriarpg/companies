/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * An order without its lines.
 */
public interface ISimpleOrder {
    /**
     * Renames the order on this object. Nothing is saved.
     *
     * @param name new name
     */
    void name(String name);

    /**
     * The id of the order.
     *
     * @return id
     */
    int id();

    /**
     * The player who placed the order. Never show the owner to the company working on the order.
     *
     * @return id of the owner
     */
    UUID owner();

    /**
     * The name of the order.
     *
     * @return name
     */
    String name();

    /**
     * The id and name of the order for display, as {@code id - name}.
     *
     * @return full name
     */
    String fullName();

    /**
     * When the order was created.
     *
     * @return creation time in UTC
     */
    LocalDateTime created();

    /**
     * The company working on the order. Never show the company to the owner.
     *
     * @return id of the company, or a value below 1 when no company claimed the order, which includes orders a player
     *     works on alone (see {@link #soloWorker()})
     */
    int company();

    /**
     * The player working on the order alone, without a company. Never show the worker to the owner or the owner to
     * the worker.
     * <p>
     * An order is claimed by a company or by a single player, never by both: while this is present,
     * {@link #company()} is below 1.
     *
     * @return id of the player, or empty when a company or nobody claimed the order
     */
    default Optional<UUID> soloWorker() {
        return Optional.empty();
    }

    /**
     * When the state of the order last changed; while it is claimed this is when it was accepted.
     *
     * @return time of the last state change, or null when unknown
     */
    LocalDateTime claimed();

    /**
     * The state of the order.
     *
     * @return state
     */
    OrderState state();

    /**
     * The fee the owner paid for placing the order, on top of its price.
     * <p>
     * It is kept when the owner cancels the order and refunded when the order is removed because nobody accepted it.
     *
     * @return fee of the order
     */
    double fee();
}
