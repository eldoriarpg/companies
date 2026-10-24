/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.session;

import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.gateway.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Verifies the Turnstile token of a login with Cloudflare's siteverify endpoint. Switched off unless
 * {@code TURNSTILE_SECRET} is set; then every token passes.
 */
public final class Turnstile {
    private static final Logger log = LoggerFactory.getLogger(Turnstile.class);
    private final ServiceConfig.TurnstileConfig config;
    private final HttpClient client;

    private record Answer(boolean success) {}

    /**
     * @param config the secret and the endpoint
     * @param client the HTTP client
     */
    public Turnstile(ServiceConfig.TurnstileConfig config, HttpClient client) {
        this.config = config;
        this.client = client;
    }

    /**
     * @param token the token the web app got from the challenge
     * @param address the client's address
     * @return whether the login may go on: always while switched off, otherwise when Cloudflare accepts the token
     */
    public boolean verify(@Nullable String token, String address) {
        if (!config.enabled()) return true;
        if (token == null || token.isBlank()) return false;
        String form =
                "secret=" + encode(config.secret()) + "&response=" + encode(token) + "&remoteip=" + encode(address);
        HttpRequest request = HttpRequest.newBuilder(config.url())
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;
            return Protocol.mapper().readValue(response.body(), Answer.class).success();
        } catch (IOException | JacksonException e) {
            log.warn("Turnstile could not be verified: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
