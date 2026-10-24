/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Posts to the webhook with the JDK's HTTP client. Connecting may take five seconds, the whole request ten.
 */
public final class HttpWebhookSender implements WebhookSender {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;
    private final URI uri;

    /**
     * @param uri the webhook url
     */
    public HttpWebhookSender(URI uri) {
        this.uri = uri;
        client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public WebhookResponse post(String json) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Companies")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, String> headers = new HashMap<>();
        response.headers().map().forEach((name, values) -> {
            if (!values.isEmpty()) headers.put(name, values.getFirst());
        });
        return new WebhookResponse(response.statusCode(), response.body(), headers);
    }
}
