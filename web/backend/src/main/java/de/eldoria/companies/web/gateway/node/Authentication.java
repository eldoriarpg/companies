/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.node;

import de.eldoria.companies.web.api.link.ErrorCode;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of checking a server's key or token.
 */
public sealed interface Authentication {
    /**
     * The server may connect.
     *
     * @param serverId the server's id at the gateway: {@code lyna:<keyId>} or {@code token:<tokenId>}, stable across
     * key rotations and token regenerations
     * @param keyKind the kind of Lyna key, {@code TRIAL} or {@code BOUGHT}; none on a self-hosted gateway
     */
    record Accepted(String serverId, @Nullable String keyKind) implements Authentication {}

    /**
     * The server may not connect.
     *
     * @param code {@link ErrorCode#UNAUTHORIZED} for a refused key, {@link ErrorCode#INTERNAL} when it could not be
     * checked
     * @param reason why, for the server's log
     */
    record Refused(ErrorCode code, String reason) implements Authentication {}
}
