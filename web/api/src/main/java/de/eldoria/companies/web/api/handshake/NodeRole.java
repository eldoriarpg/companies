/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * The role of a node in its network.
 * <p>
 * Open: a value added by a newer peer reads as {@link #UNKNOWN}. Written as the constant's name.
 */
public enum NodeRole {
    /**
     * The primary node, which runs the network's jobs.
     */
    PRIMARY,
    /**
     * Any other node.
     */
    SECONDARY,
    /**
     * A value this version does not know, sent by a newer peer.
     */
    @JsonEnumDefaultValue
    UNKNOWN
}
