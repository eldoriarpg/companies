/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.gateway.policy.VersionPolicy;
import de.eldoria.companies.web.gateway.storage.Database;
import io.javalin.Javalin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Map;

/**
 * A gateway on a random port, wired as {@link GatewayService} wires it.
 */
final class TestGateway implements AutoCloseable {
    private final GatewayApp app;
    private final Javalin javalin;
    private final ByteArrayOutputStream printed;

    private TestGateway(GatewayApp app, Javalin javalin, ByteArrayOutputStream printed) {
        this.app = app;
        this.javalin = javalin;
        this.printed = printed;
    }

    static TestGateway start(Map<String, String> env) {
        return start(env, VersionPolicy.bundled(), Clock.systemUTC());
    }

    static TestGateway start(Map<String, String> env, VersionPolicy policy, Clock clock) {
        ServiceConfig config = ServiceConfig.of(env);
        try {
            Database.open(config.database());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(printed, true, StandardCharsets.UTF_8);
        HttpClient http = HttpClient.newHttpClient();
        GatewayApp app =
                new GatewayApp(config, GatewayService.authenticator(config, http, clock, out), policy, http, clock);
        return new TestGateway(app, app.create().start(0), printed);
    }

    GatewayApp app() {
        return app;
    }

    String base() {
        return "http://localhost:" + javalin.port();
    }

    URI ws(String path) {
        return URI.create("ws://localhost:" + javalin.port() + path);
    }

    String printed() {
        return printed.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        app.closeAll();
        javalin.stop();
    }
}
