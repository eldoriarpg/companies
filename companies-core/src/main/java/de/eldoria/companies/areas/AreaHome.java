/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.configuration.elements.areas.AreaSettings;

/**
 * The rule for where a company may set its home: anywhere, unless {@code homeInsideArea} is on and the company has
 * areas, in which case the home lies inside one of them.
 */
public final class AreaHome {
    private AreaHome() {}

    /**
     * @param settings the settings of areas
     * @param hasAreas whether the company has areas
     * @param inside   whether the spot lies inside one of them
     * @return whether the home may be set there
     */
    public static boolean allowed(AreaSettings settings, boolean hasAreas, boolean inside) {
        return !settings.homeInsideArea() || !hasAreas || inside;
    }
}
