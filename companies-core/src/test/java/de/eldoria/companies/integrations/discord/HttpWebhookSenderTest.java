/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpWebhookSenderTest {
    private final AtomicReference<String> received = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/webhooks/1/token", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            var body = "{\"retry_after\":0.5}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-RateLimit-Remaining", "0");
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void postsJsonAndReadsAnswer() throws IOException, InterruptedException {
        var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/webhooks/1/token");

        var response = new HttpWebhookSender(uri).post("{\"embeds\":[]}");

        assertEquals("{\"embeds\":[]}", received.get());
        assertEquals("application/json", contentType.get());
        assertEquals(429, response.status());
        assertEquals("{\"retry_after\":0.5}", response.body());
        assertEquals("0", response.header("x-ratelimit-remaining").orElseThrow());
    }
}
