/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.api.handshake.GatewayKind;

import java.util.Locale;

/**
 * Who runs the gateway, which decides how servers authenticate.
 */
public enum GatewayMode {
    /**
     * Eldoria's gateway: servers present a Lyna dashboard key, verified with Lyna.
     */
    HOSTED(GatewayKind.HOSTED),
    /**
     * A server owner's gateway: servers present a server token the gateway generated; Lyna is never contacted.
     */
    SELF_HOSTED(GatewayKind.SELF_HOSTED);

    private final GatewayKind kind;

    GatewayMode(GatewayKind kind) {
        this.kind = kind;
    }

    /**
     * @return the kind the gateway tells nodes in its welcome
     */
    public GatewayKind kind() {
        return kind;
    }

    /**
     * @param text {@code hosted} or {@code self-hosted}, in any case, with {@code -} or {@code _}
     * @return the mode
     * @throws IllegalArgumentException for any other text
     */
    public static GatewayMode parse(String text) {
        return switch (text.strip().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "hosted" -> HOSTED;
            case "self-hosted" -> SELF_HOSTED;
            default -> throw new IllegalArgumentException("GATEWAY_MODE must be hosted or self-hosted, not " + text);
        };
    }
}
