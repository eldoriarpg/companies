/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import java.util.Optional;

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

    /**
     * Reads an area from its text. The provider is the part before the first colon, as provider ids have none.
     *
     * @param text the area as {@link #toString()} wrote it
     * @return the area, empty when the text names no provider or no reference
     */
    public static Optional<AreaRef> parse(String text) {
        var colon = text.indexOf(':');
        if (colon <= 0 || colon == text.length() - 1) return Optional.empty();
        return Optional.of(new AreaRef(text.substring(0, colon), text.substring(colon + 1)));
    }
}
