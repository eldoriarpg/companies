/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonValue;
import de.eldoria.companies.web.api.Wire;

/**
 * Who runs the gateway a server is connected to.
 * <p>
 * Open: a value added by a newer peer reads as {@link #UNKNOWN}. Written in lower case with hyphens.
 */
public enum GatewayKind {
    /**
     * The gateway run by Eldoria, which verifies dashboard keys with Lyna.
     */
    HOSTED,
    /**
     * A gateway run by the server owner, which never contacts Lyna.
     */
    SELF_HOSTED,
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
