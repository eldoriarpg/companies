/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

/**
 * The gateway's paths. Every answer below {@code /api} carries {@code Cache-Control: no-store}, except icons.
 */
public final class Routes {
    /**
     * Websocket of the nodes; the protocol of {@code web-api}.
     */
    public static final String CONNECT = "/api/v1/servers/connect";

    /**
     * {@code GET}: a pending session, its server's branding and version status, and the Turnstile site key.
     */
    public static final String PENDING = "/api/v1/s/{shortId}";

    /**
     * {@code POST {code, turnstileToken}}: logs in with the code and sets the session cookie.
     */
    public static final String LOGIN = "/api/v1/s/{shortId}/login";

    /**
     * {@code GET}: the session's server: branding, features, limits, version status and deprecated parts.
     */
    public static final String SERVER = "/api/v1/me/server";

    /**
     * {@code POST}: ends the session.
     */
    public static final String LOGOUT = "/api/v1/me/logout";

    /**
     * {@code GET}: the session's snapshot.
     */
    public static final String SNAPSHOT = "/api/v1/me/snapshot";

    /**
     * {@code POST {name, args}}: a read action forwarded to the server.
     */
    public static final String ACTIONS = "/api/v1/me/actions";

    /**
     * Websocket of the browser: patches and session events.
     */
    public static final String STREAM = "/api/v1/me/stream";

    /**
     * {@code GET}: a server icon by its hash, cacheable for good.
     */
    public static final String ICON = "/api/v1/icons/{hash}";

    /**
     * {@code GET}: 200 while the database answers, 503 otherwise; for container health checks.
     */
    public static final String HEALTH = "/api/v1/health";

    private Routes() {}
}
