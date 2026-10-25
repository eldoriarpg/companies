/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The node a connection belongs to.
 *
 * @param uid the node's {@code nodeUid}
 * @param role its role in the network
 * @param name the name the server's admins gave the node in {@code node.yml}, none when they gave none
 */
public record NodeInfo(UUID uid, NodeRole role, @Nullable String name) {
    /**
     * A node without a name.
     *
     * @param uid  the node's {@code nodeUid}
     * @param role its role in the network
     */
    public NodeInfo(UUID uid, NodeRole role) {
        this(uid, role, null);
    }
}
