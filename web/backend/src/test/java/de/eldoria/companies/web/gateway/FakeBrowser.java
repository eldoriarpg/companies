/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.gateway.browser.BrowserApi;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A browser for tests: keeps the session cookie the gateway sets and comes from its own address.
 */
final class FakeBrowser {
    static final String FIREFOX_ON_ANDROID = "Mozilla/5.0 (Android 14; Mobile; rv:131.0) Gecko/131.0 Firefox/131.0";
    private static final JsonMapper MAPPER = Protocol.mapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private final TestGateway gateway;
    private final String address;
    private String origin = "https://dash.test";
    private @Nullable String cookie;

    FakeBrowser(TestGateway gateway, String address) {
        this.gateway = gateway;
        this.address = address;
    }

    HttpResponse<String> get(String path) {
        return send(request(path).GET());
    }

    HttpResponse<String> post(String path, String body) {
        return send(request(path).POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    CompletableFuture<HttpResponse<String>> postAsync(String path, String body) {
        return CompletableFuture.supplyAsync(() -> post(path, body));
    }

    CompletableFuture<HttpResponse<String>> getAsync(String path) {
        return CompletableFuture.supplyAsync(() -> get(path));
    }

    void origin(String origin) {
        this.origin = origin;
    }

    @Nullable
    String cookie() {
        return cookie;
    }

    void cookie(@Nullable String cookie) {
        this.cookie = cookie;
    }

    static JsonNode json(HttpResponse<String> response) {
        return MAPPER.readTree(response.body());
    }

    static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }

    Stream stream() {
        return new Stream();
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(gateway.base() + path))
                .header("X-Forwarded-For", address)
                .header("User-Agent", FIREFOX_ON_ANDROID)
                .header("Origin", origin)
                .header("Content-Type", "application/json");
        if (cookie != null) request.header("Cookie", BrowserApi.COOKIE + "=" + cookie);
        return request;
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            response.headers().allValues("Set-Cookie").stream()
                    .filter(header -> header.startsWith(BrowserApi.COOKIE + "="))
                    .findFirst()
                    .ifPresent(header -> {
                        String value = header.substring(BrowserApi.COOKIE.length() + 1, header.indexOf(';'));
                        cookie = value.isEmpty() ? null : value;
                    });
            return response;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The browser's stream on {@code /api/v1/me/stream}.
     */
    final class Stream implements AutoCloseable {
        private final LinkedBlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private final WebSocket socket;

        private Stream() {
            WebSocket.Builder builder = client.newWebSocketBuilder().header("Origin", origin);
            if (cookie != null) builder.header("Cookie", BrowserApi.COOKIE + "=" + cookie);
            socket = builder.buildAsync(gateway.ws(Routes.STREAM), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            inbox.add(data.toString());
                            webSocket.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            closed.complete(statusCode);
                            return null;
                        }
                    })
                    .join();
        }

        JsonNode next() {
            try {
                String text = inbox.poll(10, TimeUnit.SECONDS);
                assertNotNull(text, "The stream sent nothing");
                return MAPPER.readTree(text);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        void ping() {
            socket.sendText("{\"type\":\"ping\",\"id\":\"b-1\",\"v\":1,\"time\":\"2026-10-24T12:00:00Z\"}", true)
                    .join();
        }

        int awaitClose() {
            return closed.orTimeout(10, TimeUnit.SECONDS).join();
        }

        @Override
        public void close() {
            if (!socket.isOutputClosed()) socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            closed.completeOnTimeout(-1, 2, TimeUnit.SECONDS).join();
        }
    }
}
