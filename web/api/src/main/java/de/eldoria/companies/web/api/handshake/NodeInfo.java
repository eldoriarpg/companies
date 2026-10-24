/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

import java.util.UUID;

/**
 * The node a connection belongs to.
 *
 * @param uid the node's {@code nodeUid}
 * @param role its role in the network
 */
public record NodeInfo(UUID uid, NodeRole role) {}
