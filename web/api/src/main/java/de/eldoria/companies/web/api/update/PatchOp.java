/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.update;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonValue;
import de.eldoria.companies.web.api.Wire;

/**
 * How a patch changes the value at its path.
 * <p>
 * Open: a value added by a newer peer reads as {@link #UNKNOWN}. Written in lower case with hyphens.
 */
public enum PatchOp {
    /**
     * Adds the list entry with the patch's key, or replaces it.
     */
    UPSERT,
    /**
     * Removes the list entry with the patch's key, or the value at the path.
     */
    REMOVE,
    /**
     * Replaces the whole value at the path.
     */
    REPLACE,
    /**
     * A value this version does not know, sent by a newer peer.
     */
    @JsonEnumDefaultValue
    UNKNOWN;

    /**
     * The value as written on the wire.
     *
     * @return the constant's wire name
     */
    @JsonValue
    public String wire() {
        return Wire.name(this);
    }
}
