/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.browser;

import de.eldoria.companies.web.api.handshake.DeprecatedPart;
import de.eldoria.companies.web.api.handshake.Limits;
import de.eldoria.companies.web.api.handshake.VersionStatus;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;

/**
 * The bodies of the browser API. Absent values are left out of the JSON.
 */
public final class Views {
    private Views() {}

    /**
     * A server's branding.
     *
     * @param name the server's name
     * @param iconUrl the path of its icon, {@code /api/v1/icons/<hash>}; none without one
     */
    public record Brand(String name, @Nullable String iconUrl) {}

    /**
     * {@code GET /api/v1/s/{shortId}}: a session waiting for its code.
     *
     * @param server the server it belongs to
     * @param expiresAt when the code stops working
     * @param attemptsLeft how many codes may still be tried
     * @param turnstileSiteKey the site key of the login challenge; none when Turnstile is off
     * @param version the state of the server's plugin version
     */
    public record Pending(
            Brand server,
            Instant expiresAt,
            int attemptsLeft,
            @Nullable String turnstileSiteKey,
            VersionStatus version) {}

    /**
     * {@code POST /api/v1/s/{shortId}/login}.
     *
     * @param code the six-letter code
     * @param turnstileToken the Turnstile token, when Turnstile is on
     */
    public record LoginRequest(
            @Nullable String code, @Nullable String turnstileToken) {}

    /**
     * {@code GET /api/v1/me/server}, and the {@code server} stream event.
     *
     * @param server the server's branding
     * @param features the features both the server and the gateway support; the web app shows only these
     * @param limits the server's limits
     * @param version the state of the server's plugin version
     * @param deprecated the deprecated parts the server still uses
     * @param online whether a node of the server takes requests
     */
    public record ServerInfo(
            Brand server,
            List<String> features,
            Limits limits,
            VersionStatus version,
            List<DeprecatedPart> deprecated,
            boolean online) {}

    /**
     * {@code POST /api/v1/me/actions}.
     *
     * @param name the action
     * @param args its arguments
     */
    public record ActionCall(
            @Nullable String name, @Nullable ObjectNode args) {}

    /**
     * Every error of the browser API.
     *
     * @param error the code, see {@link ApiError.Code}
     * @param message a plain sentence
     * @param attemptsLeft for {@link ApiError.Code#WRONG_CODE}, how many codes may still be tried
     * @param version for {@link ApiError.Code#DISCONTINUED}, the state of the server's plugin version
     */
    public record ErrorView(
            ApiError.Code error,
            @Nullable String message,
            @Nullable Integer attemptsLeft,
            @Nullable VersionStatus version) {}
}
