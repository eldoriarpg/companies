/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * What kind of protocol part is deprecated.
 * <p>
 * Open: a value added by a newer peer reads as {@link #UNKNOWN}. Written as the constant's name.
 */
public enum PartKind {
    /**
     * A field of a message or of a payload record.
     */
    FIELD,
    /**
     * A message type.
     */
    MESSAGE,
    /**
     * A feature name, see {@link de.eldoria.companies.web.api.Features}.
     */
    FEATURE,
    /**
     * A protocol version.
     */
    PROTOCOL,
    /**
     * A value this version does not know, sent by a newer peer.
     */
    @JsonEnumDefaultValue
    UNKNOWN
}
