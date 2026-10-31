/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.area;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * An area the server offers companies to buy or rent.
 */
public interface IAreaOffer {
    /**
     * The id of the offer.
     *
     * @return the id
     */
    int id();

    /**
     * The plugin the area comes from.
     *
     * @return the id of the provider, lower case
     */
    String provider();

    /**
     * The provider's reference of the area.
     *
     * @return the reference
     */
    String ref();

    /**
     * The world the area lies in.
     *
     * @return the name of the world
     */
    String world();

    /**
     * The server whose world holds the area.
     *
     * @return the node id of that server
     */
    UUID node();

    /**
     * Whether companies buy or rent the area.
     *
     * @return the kind of offer
     */
    OfferKind kind();

    /**
     * The price of a sale, or the rent per period.
     *
     * @return the price
     */
    double price();

    /**
     * How long one rent period lasts.
     *
     * @return the days of a period, 0 for a sale
     */
    int periodDays();

    /**
     * Who keeps the area for the server and gets it back when a rent ends or the company sells it back.
     *
     * @return the keeper, empty for nobody
     */
    Optional<UUID> keeper();

    /**
     * When the area was offered.
     *
     * @return the time
     */
    LocalDateTime created();
}
