/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.browser;

import de.eldoria.companies.web.api.handshake.DeprecatedPart;
import de.eldoria.companies.web.api.handshake.GatewayKind;
import de.eldoria.companies.web.api.handshake.VersionStatus;
import de.eldoria.companies.web.api.session.PlayerRef;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/**
 * The bodies of the browser API. Absent values are left out of the JSON.
 */
public final class Views {
    private Views() {}

    /**
     * {@code GET /api/v1/config}: what the web app needs before a login.
     *
     * @param gateway hosted or self-hosted
     * @param turnstileSiteKey the site key of the login challenge; none when Turnstile is off
     */
    public record ConfigView(GatewayKind gateway, @Nullable String turnstileSiteKey) {}

    /**
     * A server as the browser shows it.
     *
     * @param name the server's name; none for the default look
     * @param icon the path of its icon, {@code /api/v1/icons/<hash>}; none without one
     * @param online whether a node of it takes requests
     * @param version the state of its plugin version; none while it is offline
     * @param deprecated the deprecated parts it still uses, for the banner shown to admins
     */
    public record ServerView(
            @Nullable String name,
            @Nullable String icon,
            boolean online,
            @Nullable VersionStatus version,
            List<DeprecatedPart> deprecated) {}

    /**
     * {@code GET /api/v1/s/{shortId}}: a session waiting for its code.
     *
     * @param shortId the short id
     * @param server the server it belongs to
     * @param expiresAt when the code stops working
     */
    public record PendingView(String shortId, ServerView server, Instant expiresAt) {}

    /**
     * {@code POST /api/v1/s/{shortId}/login}.
     *
     * @param code the six-letter code
     * @param turnstile the Turnstile token, when Turnstile is on
     */
    public record LoginRequest(
            @Nullable String code, @Nullable String turnstile) {}

    /**
     * {@code GET /api/v1/me}: the logged in session.
     *
     * @param player the player
     * @param server the server
     * @param features the features both the server and the gateway support; the web app shows only these
     * @param expiresAt when the session ends unless it is used
     */
    public record MeView(PlayerRef player, ServerView server, List<String> features, Instant expiresAt) {}

    /**
     * A page of a list.
     *
     * @param page the page, from 1
     * @param pages how many pages there are
     * @param total how many entries there are
     * @param entries the entries of the page
     */
    public record PageView(int page, int pages, int total, List<JsonNode> entries) {}

    /**
     * {@code POST /api/v1/me/actions/{name}}: the plugin's answer.
     *
     * @param ok whether the action succeeded
     * @param reason the message key of a refusal
     * @param message the refusal as plain text
     * @param data what the action returns
     */
    public record ActionView(
            boolean ok,
            @Nullable String reason,
            @Nullable String message,
            @Nullable JsonNode data) {}

    /**
     * Every error of the browser API.
     *
     * @param error a stable code, e.g. {@code wrong-code}
     * @param message a plain sentence
     * @param attemptsLeft for {@code wrong-code}, how many codes may still be tried
     */
    public record ErrorView(
            String error, String message, @Nullable Integer attemptsLeft) {}
}
