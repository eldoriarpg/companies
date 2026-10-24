/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.keys;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lyna's key refresh endpoint as a local HTTP server, answering what the test sets and recording what it was asked.
 */
final class FakeLyna implements AutoCloseable {
    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String body = "{}";

    private FakeLyna(HttpServer server) {
        this.server = server;
    }

    static FakeLyna start() throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var lyna = new FakeLyna(server);
        server.createContext(HttpLynaKeys.REFRESH_PATH, exchange -> {
            lyna.requests.add(new Request(
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("User-Agent")));
            var answer = lyna.body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(lyna.status, answer.length);
            try (var out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        server.start();
        return lyna;
    }

    URI address() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    void answer(int status, String body) {
        this.status = status;
        this.body = body;
    }

    List<Request> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /**
     * A request Lyna received.
     *
     * @param method        the HTTP method
     * @param authorization the authorization header
     * @param userAgent     the user agent header
     */
    record Request(String method, String authorization, String userAgent) {}
}
