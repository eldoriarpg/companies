/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Where a plugin version stands in the gateway's version policy.
 * <p>
 * Open: a value added by a newer peer reads as {@link #UNKNOWN}. Written as the constant's name.
 */
public enum VersionState {
    /**
     * The dashboard works without restrictions.
     */
    SUPPORTED,
    /**
     * The dashboard works and shows a warning with the date the version is discontinued.
     */
    DEPRECATED,
    /**
     * The dashboard does not open; the gateway accepts no other messages from the connection.
     */
    DISCONTINUED,
    /**
     * A value this version does not know, sent by a newer peer.
     */
    @JsonEnumDefaultValue
    UNKNOWN
}
