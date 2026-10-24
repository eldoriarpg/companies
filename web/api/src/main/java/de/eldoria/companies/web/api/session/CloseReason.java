/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.session;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonValue;
import de.eldoria.companies.web.api.Wire;

/**
 * Why a dashboard session ended.
 * <p>
 * Open: a value added by a newer peer reads as {@link #UNKNOWN}. Written in lower case with hyphens.
 */
public enum CloseReason {
    /**
     * The player logged out of the dashboard.
     */
    LOGOUT,
    /**
     * The session was idle longer than allowed.
     */
    IDLE,
    /**
     * The session reached its maximum age, or its login code expired unused.
     */
    EXPIRED,
    /**
     * An admin removed the player's access.
     */
    REVOKED,
    /**
     * The server was disconnected longer than the grace period.
     */
    CONNECTION_LOST,
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
