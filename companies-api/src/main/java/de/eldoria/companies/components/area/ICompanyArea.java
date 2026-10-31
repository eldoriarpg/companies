/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.area;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An area a company uses: a plot or region of an {@link AreaProvider}, on the server whose world holds it.
 */
public interface ICompanyArea {
    /**
     * The id of the area.
     *
     * @return the id
     */
    int id();

    /**
     * The company that uses the area.
     *
     * @return the id of the company
     */
    int company();

    /**
     * The provider of the area.
     *
     * @return the id of the provider, lower case
     */
    String provider();

    /**
     * The provider's reference of the area as last stored. After plots were merged, it is the name
     * {@link AreaProvider#canonical(String)} gives once the area's server checked it again.
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
     * @return the node id of the server
     */
    UUID node();

    /**
     * How the company got the area.
     *
     * @return the source
     */
    AreaSource source();

    /**
     * What the company paid for the area.
     *
     * @return the price of a bought area, the rent per period of a rented one, 0 for a linked one
     */
    double price();

    /**
     * Until when a rented area is paid, in UTC.
     *
     * @return the end of the period paid, null unless the area is rented
     */
    @Nullable
    LocalDateTime rentUntil();

    /**
     * Whether a rented area is paid for another period when it is due.
     *
     * @return true for a rent that renews; false for a rent that runs out and for an area that is not rented
     */
    boolean renews();

    /**
     * When the company got the area, in UTC.
     *
     * @return the time
     */
    LocalDateTime created();
}
