/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonValue;
import de.eldoria.companies.web.api.Wire;

/**
 * How players join a company.
 * <p>
 * Open: a value added by a newer peer reads as {@link #UNKNOWN}. Written in lower case with hyphens.
 */
public enum JoinMode {
    /**
     * Anyone may join.
     */
    OPEN,
    /**
     * Players apply and a member with the invite permission accepts.
     */
    APPLICATIONS,
    /**
     * Only invited players join.
     */
    INVITE_ONLY,
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
