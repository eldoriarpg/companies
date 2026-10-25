/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.api.Protocol;
import io.javalin.Javalin;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lyna's key verification as recorded in its contract: {@code POST /api/v1/keys/verify} with the key type's
 * gateway secret as bearer token; 401 for a wrong secret, 400 without a key, otherwise 200 with {@code valid}.
 */
final class FakeLyna implements AutoCloseable {
    static final String SECRET = "lyg_fake-gateway-secret";
    private final Map<String, String> answers = new ConcurrentHashMap<>();
    private final Set<String> failing = ConcurrentHashMap.newKeySet();
    private final AtomicInteger calls = new AtomicInteger();
    private final Javalin app;

    FakeLyna() {
        app = Javalin.create(config -> config.routes.post("/api/v1/keys/verify", ctx -> {
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
        answer(key, keyId, product, "dashboard", kind, false);
    }

    void ofType(String key, String keyId, String type) {
        answer(key, keyId, 2, type, "BOUGHT", false);
    }

    void rotated(String key, String keyId) {
        answer(key, keyId, 2, "dashboard", "BOUGHT", true);
    }

    private void answer(String key, String keyId, int product, String type, String kind, boolean rotated) {
        answers.put(key, """
                {"valid":true,"product":%d,"type":"%s","kind":"%s","keyId":"%s","name":"Test key",
                 "refreshAt":"2026-10-31T00:00:00Z","rotated":%s}""".formatted(product, type, kind, keyId, rotated));
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
