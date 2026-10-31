/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.area;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The home of a company: the spot its members teleport to with {@code /company home}, on the server it was set on.
 */
public interface ICompanyHome {
    /**
     * The company of the home.
     *
     * @return the id of the company
     */
    int company();

    /**
     * The server the home was set on.
     *
     * @return the node id of the server
     */
    UUID node();

    /**
     * The name of that server in the proxy.
     *
     * @return the name, empty when the server has none
     */
    String server();

    /**
     * The world of the home.
     *
     * @return the name of the world
     */
    String world();

    /**
     * The x coordinate.
     *
     * @return x
     */
    double x();

    /**
     * The y coordinate.
     *
     * @return y
     */
    double y();

    /**
     * The z coordinate.
     *
     * @return z
     */
    double z();

    /**
     * Where a player teleported home looks, sideways.
     *
     * @return the yaw
     */
    float yaw();

    /**
     * Where a player teleported home looks, up and down.
     *
     * @return the pitch
     */
    float pitch();

    /**
     * The member who set the home.
     *
     * @return the id of the player
     */
    UUID setBy();

    /**
     * When the home was set, in UTC.
     *
     * @return the time
     */
    LocalDateTime setAt();
}
