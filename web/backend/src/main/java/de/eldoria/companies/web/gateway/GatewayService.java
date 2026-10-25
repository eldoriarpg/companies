/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.gateway.node.LynaAuthenticator;
import de.eldoria.companies.web.gateway.node.ServerAuthenticator;
import de.eldoria.companies.web.gateway.node.TokenAuthenticator;
import de.eldoria.companies.web.gateway.policy.VersionPolicy;
import de.eldoria.companies.web.gateway.storage.Database;
import de.eldoria.companies.web.gateway.storage.TokenRepository;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Entry point of the dashboard gateway: reads the settings from the environment ({@link ServiceConfig}), migrates the
 * database and starts the gateway. A hosted gateway needs {@code LYNA_GATEWAY_SECRET}. A self-hosted gateway
 * creates a server token named {@code default} on its first start and prints it once.
 * <p>
 * With arguments it manages the server tokens of a self-hosted gateway instead and exits:
 * <pre>
 * token list
 * token create &lt;name&gt;
 * token regenerate &lt;name&gt;
 * token delete &lt;name&gt;
 * </pre>
 */
public final class GatewayService {
    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);
    private static final long SWEEP_SECONDS = 5;

    private GatewayService() {}

    /**
     * @param args none to run the gateway, or a token command
     * @throws SQLException when the database cannot be reached
     * @throws IOException when the version policy or a migration cannot be read
     */
    public static void main(String[] args) throws SQLException, IOException {
        ServiceConfig config = ServiceConfig.of(System.getenv());
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        if (args.length > 0) {
            Database.open(config.database());
            System.exit(tokens(Arrays.asList(args), new TokenRepository(), out));
            return;
        }
        if (config.mode() == GatewayMode.HOSTED && config.lyna().secret().isBlank()) {
            log.error("A hosted gateway needs LYNA_GATEWAY_SECRET; set GATEWAY_MODE=self-hosted to run your own");
            System.exit(1);
            return;
        }
        Path policyFile = config.versionPolicy();
        VersionPolicy policy = policyFile == null ? VersionPolicy.bundled() : VersionPolicy.read(policyFile);
        Database.open(config.database());
        Clock clock = Clock.systemUTC();
        HttpClient http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        GatewayApp gateway = new GatewayApp(config, authenticator(config, http, clock, out), policy, http, clock);
        gateway.resume();
        Javalin app = gateway.create().start(config.port());
        log.info(
                "Gateway started in {} mode on port {}, links start with {}",
                config.mode(),
                config.port(),
                config.publicUrl());
        ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor();
        sweeper.scheduleAtFixedRate(gateway::sweep, SWEEP_SECONDS, SWEEP_SECONDS, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sweeper.shutdownNow();
            gateway.closeAll();
            app.stop();
        }));
    }

    /**
     * Picks how servers authenticate. A self-hosted gateway without any token creates one named {@code default} and
     * prints it.
     *
     * @param config the settings
     * @param http the HTTP client for Lyna
     * @param clock the time
     * @param out where a new token is printed
     * @return the authenticator of the configured mode
     */
    static ServerAuthenticator authenticator(ServiceConfig config, HttpClient http, Clock clock, PrintStream out) {
        return switch (config.mode()) {
            case HOSTED -> new LynaAuthenticator(config.lyna(), http, clock);
            case SELF_HOSTED -> {
                TokenRepository tokens = new TokenRepository();
                if (tokens.count() == 0) tokens.create("default").ifPresent(token -> announce(out, token));
                yield new TokenAuthenticator(tokens);
            }
        };
    }

    /**
     * Runs a token command.
     *
     * @param args the command and its arguments
     * @param tokens the stored tokens
     * @param out where the result is printed
     * @return the exit code
     */
    static int tokens(List<String> args, TokenRepository tokens, PrintStream out) {
        String usage = "Usage: token list | token create <name> | token regenerate <name> | token delete <name>";
        if (args.size() < 2 || !args.getFirst().equals("token")) {
            out.println(usage);
            return 2;
        }
        String command = args.get(1);
        if (command.equals("list")) {
            for (TokenRepository.StoredToken token : tokens.list()) {
                out.println(token.name() + "\tcreated " + token.created());
            }
            return 0;
        }
        if (args.size() != 3) {
            out.println(usage);
            return 2;
        }
        String name = args.get(2);
        Optional<String> token;
        switch (command) {
            case "create" -> token = tokens.create(name);
            case "regenerate" -> token = tokens.regenerate(name);
            case "delete" -> {
                boolean deleted = tokens.delete(name);
                out.println(deleted ? "Deleted the token of " + name : "No token named " + name);
                return deleted ? 0 : 1;
            }
            default -> {
                out.println(usage);
                return 2;
            }
        }
        if (token.isEmpty()) {
            out.println(command.equals("create") ? "A token named " + name + " exists" : "No token named " + name);
            return 1;
        }
        announce(out, token.get());
        return 0;
    }

    private static void announce(PrintStream out, String token) {
        out.println();
        out.println("Server token (shown only now, keep it secret):");
        out.println("    " + token);
        out.println("Give it to the Companies plugin with /companyadmin dashboard key <token>.");
        out.println();
    }
}
