/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.node;

import de.eldoria.companies.web.api.handshake.Credentials;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.gateway.storage.TokenRepository;

/**
 * Checks the server tokens of a self-hosted gateway against the stored hashes. Each token is one network.
 */
public final class TokenAuthenticator implements ServerAuthenticator {
    private final TokenRepository tokens;

    /**
     * @param tokens the stored tokens
     */
    public TokenAuthenticator(TokenRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    public Authentication authenticate(Credentials credentials) {
        if (credentials.kind() != KeyKind.SERVER_TOKEN) {
            return new Authentication.Refused(
                    ErrorCode.UNAUTHORIZED,
                    "This gateway is self-hosted and needs a server token, not a dashboard key");
        }
        return tokens.find(credentials.key())
                .<Authentication>map(token -> new Authentication.Accepted("token:" + token.id(), null))
                .orElseGet(() -> new Authentication.Refused(ErrorCode.UNAUTHORIZED, "Unknown server token"));
    }
}
