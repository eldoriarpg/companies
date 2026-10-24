/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.api.Protocol;
import io.javalin.Javalin;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lyna's dashboard key verification as recorded in its contract: {@code POST /api/v1/dashboard/key/verify} with the
 * gateway secret as bearer token; 401 for a wrong secret, 400 without a key, otherwise 200 with {@code valid}.
 */
final class FakeLyna implements AutoCloseable {
    static final String SECRET = "fake-gateway-secret";
    private final Map<String, String> answers = new ConcurrentHashMap<>();
    private final Set<String> failing = ConcurrentHashMap.newKeySet();
    private final AtomicInteger calls = new AtomicInteger();
    private final Javalin app;

    FakeLyna() {
        app = Javalin.create(config -> config.routes.post("/api/v1/dashboard/key/verify", ctx -> {
                    calls.incrementAndGet();
                    if (!("Bearer " + SECRET).equals(ctx.header("Authorization"))) {
                        ctx.status(401).result("{\"reason\":\"UNAUTHORIZED\"}");
                        return;
                    }
                    String key =
                            Protocol.mapper().readTree(ctx.body()).path("key").asString("");
                    if (key.isEmpty()) {
                        ctx.status(400);
                        return;
                    }
                    if (failing.contains(key)) {
                        ctx.status(503);
                        return;
                    }
                    ctx.contentType("application/json")
                            .result(answers.getOrDefault(key, "{\"valid\":false,\"reason\":\"UNKNOWN\"}"));
                }))
                .start(0);
    }

    void valid(String key, String keyId, int product, String kind) {
        answers.put(key, """
                {"valid":true,"product":%d,"kind":"%s","keyId":"%s","name":"Test key",
                 "refreshAt":"2026-10-31T00:00:00Z","rotated":false}""".formatted(product, kind, keyId));
    }

    void invalid(String key, String reason) {
        answers.put(key, "{\"valid\":false,\"reason\":\"%s\"}".formatted(reason));
    }

    void down(String key) {
        failing.add(key);
    }

    int calls() {
        return calls.get();
    }

    String url() {
        return "http://localhost:" + app.port();
    }

    @Override
    public void close() {
        app.stop();
    }
}
