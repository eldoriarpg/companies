/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

/**
 * An area as Companies stores it: the provider it comes from and the reference that provider gave it.
 *
 * @param provider id of the provider, lower case
 * @param ref      the provider's reference of the area
 */
public record AreaRef(String provider, String ref) {
    /**
     * @return the area as one text, {@code provider:ref}
     */
    @Override
    public String toString() {
        return provider + ":" + ref;
    }
}
