/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.keys;

import de.eldoria.companies.web.api.Protocol;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Asks Lyna over HTTP with the JDK's client. Connecting may take five seconds, the whole request ten. The key travels
 * only in the {@code Authorization} header and is never part of an exception message.
 */
public final class HttpLynaKeys implements LynaKeys {
    /**
     * Lyna, which issues and rotates the dashboard keys.
     */
    public static final URI LYNA = URI.create("https://lyna.eldoria.de");

    /**
     * The path of the refresh endpoint below Lyna's address.
     */
    public static final String REFRESH_PATH = "/api/v1/keys/refresh";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long MILLIS_THRESHOLD = 100_000_000_000L;

    private final HttpClient client;
    private final URI refresh;
    private final String userAgent;

    /**
     * @param base      Lyna's address, {@link #LYNA} outside of tests
     * @param userAgent the {@code User-Agent} of the requests
     */
    public HttpLynaKeys(URI base, String userAgent) {
        this.userAgent = userAgent;
        refresh = base.resolve(REFRESH_PATH);
        client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Answer refresh(String key) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(refresh)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + key)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return answer(response.statusCode(), response.body());
    }

    /**
     * Reads Lyna's answer.
     *
     * @param status the HTTP status
     * @param body   the body
     * @return the answer
     * @throws IOException when the answer is none Lyna gives by its contract
     */
    static Answer answer(int status, String body) throws IOException {
        if (status == 401 || status == 403 || status == 429) return new Refused(status, reason(body));
        if (status != 200) throw new IOException("Lyna answered the key refresh with " + status + ".");
        var json = read(body);
        var key = json.path("key").asString("");
        if (key.isBlank()) throw new IOException("Lyna answered the key refresh without a key.");
        return new Issued(key, instant(json.path("refreshAt")));
    }

    private static String reason(String body) {
        try {
            var reason = read(body).path("reason").asString("");
            return reason.isBlank() ? "UNKNOWN" : reason;
        } catch (IOException e) {
            return "UNKNOWN";
        }
    }

    private static JsonNode read(String body) throws IOException {
        try {
            return Protocol.mapper().readTree(body);
        } catch (JacksonException e) {
            throw new IOException("Lyna's answer to the key refresh is no JSON.");
        }
    }

    private static Instant instant(JsonNode value) throws IOException {
        if (value.isNumber()) {
            var number = value.asLong();
            return number > MILLIS_THRESHOLD ? Instant.ofEpochMilli(number) : Instant.ofEpochSecond(number);
        }
        var text = value.asString("");
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            throw new IOException("Lyna answered the key refresh without a valid refresh time.");
        }
    }
}
