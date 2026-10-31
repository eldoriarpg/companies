/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import org.bukkit.Location;

import java.util.Objects;
import java.util.UUID;

/**
 * A home to set through {@link de.eldoria.companies.api.CompanyApi#setHome(HomeRequest)}. A company with areas sets
 * it inside one of them while {@code homeInsideArea} is on; the setter needs no company permission.
 *
 * @param company  the id of the company
 * @param location where the home goes, on this server; copied, so later changes to it do not move the home
 * @param by       who sets it, shown as the setter
 */
public record HomeRequest(int company, Location location, UUID by) {
    /**
     * Validates the request.
     *
     * @throws IllegalArgumentException when the location has no world
     */
    public HomeRequest {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(by, "by");
        if (location.getWorld() == null) throw new IllegalArgumentException("The location needs a world");
        location = location.clone();
    }
}
