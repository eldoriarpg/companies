/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.node;

import de.eldoria.companies.web.api.handshake.Credentials;

/**
 * Checks the key or token a node presents in its hello. Every new connection is checked again.
 */
public interface ServerAuthenticator {
    /**
     * @param credentials what the node presented
     * @return whether and as which server it may connect
     */
    Authentication authenticate(Credentials credentials);
}
