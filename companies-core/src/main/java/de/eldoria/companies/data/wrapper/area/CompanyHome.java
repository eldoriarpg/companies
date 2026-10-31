/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.area;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The home of a company, stored in {@code company_homes}: a spot its members teleport to, on the server it was set on.
 *
 * @param company id of the company
 * @param node    the server it was set on
 * @param server  the name of that server in the proxy, empty when it has none
 * @param world   the world
 * @param x       x
 * @param y       y
 * @param z       z
 * @param yaw     where the player looks, sideways
 * @param pitch   where the player looks, up and down
 * @param setBy   the member who set it
 * @param setAt   when it was set
 */
public record CompanyHome(
        int company,
        UUID node,
        String server,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        UUID setBy,
        LocalDateTime setAt) {}
