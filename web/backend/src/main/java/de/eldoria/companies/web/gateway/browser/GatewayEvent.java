/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.browser;

import org.jspecify.annotations.Nullable;

/**
 * The stream messages of the gateway itself, next to the protocol's {@code patch}, {@code pong},
 * {@code session.closed} and {@code error}.
 * <ul>
 *     <li>{@code resync}: the snapshot was replaced, after a sequence gap, a patch that did not fit or a node that
 *     connected again; the browser loads it again.</li>
 *     <li>{@code server}: the server's branding, version status, features or connection changed.</li>
 *     <li>{@code presence}: the session's player came online on the server or went offline, which decides whether the
 *     actions that move items can run.</li>
 * </ul>
 *
 * @param type {@code resync}, {@code server} or {@code presence}
 * @param server the server, for {@code server}
 * @param online whether the player is online, for {@code presence}
 */
public record GatewayEvent(
        String type,
        Views.@Nullable ServerInfo server,
        @Nullable Boolean online) {
    /**
     * @return the event telling the browser to load the snapshot again
     */
    public static GatewayEvent resync() {
        return new GatewayEvent("resync", null, null);
    }

    /**
     * @param server the server as it is now
     * @return the event
     */
    public static GatewayEvent server(Views.ServerInfo server) {
        return new GatewayEvent("server", server, null);
    }

    /**
     * @param online whether the session's player is now online on the server
     * @return the event
     */
    public static GatewayEvent presence(boolean online) {
        return new GatewayEvent("presence", null, online);
    }
}
