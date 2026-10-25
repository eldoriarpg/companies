/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.session.CreateSession;
import de.eldoria.companies.web.api.session.SessionClosed;
import de.eldoria.companies.web.api.session.SessionsOpen;
import de.eldoria.companies.web.api.session.SessionsRevoked;
import de.eldoria.companies.web.api.update.Patch;
import de.eldoria.companies.web.gateway.browser.ApiError;
import de.eldoria.companies.web.gateway.browser.BrowserApi;
import de.eldoria.companies.web.gateway.browser.BrowserStreams;
import de.eldoria.companies.web.gateway.browser.GatewayEvent;
import de.eldoria.companies.web.gateway.node.LiveServer;
import de.eldoria.companies.web.gateway.node.NodeEvents;
import de.eldoria.companies.web.gateway.node.NodeHub;
import de.eldoria.companies.web.gateway.node.NodeLink;
import de.eldoria.companies.web.gateway.node.ServerAuthenticator;
import de.eldoria.companies.web.gateway.policy.VersionPolicy;
import de.eldoria.companies.web.gateway.session.Sessions;
import de.eldoria.companies.web.gateway.session.Turnstile;
import de.eldoria.companies.web.gateway.snapshot.ReadRelay;
import de.eldoria.companies.web.gateway.snapshot.SnapshotCache;
import de.eldoria.companies.web.gateway.storage.Database;
import de.eldoria.companies.web.gateway.storage.GatewayStateRepository;
import de.eldoria.companies.web.gateway.storage.IconRepository;
import de.eldoria.companies.web.gateway.storage.ServerRepository;
import de.eldoria.companies.web.gateway.storage.SessionRepository;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson3;
import io.javalin.websocket.WsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The gateway: the nodes' websocket ({@link NodeHub}), the sessions ({@link Sessions}), the snapshot cache
 * ({@link SnapshotCache}) and the browser API ({@link BrowserApi}), wired together on one Javalin app.
 * <p>
 * Everything live (connections, snapshots, streams) is in memory, so the gateway runs as a single instance;
 * sessions, servers, tokens and icons are in the database.
 */
public final class GatewayApp {
    private static final Logger log = LoggerFactory.getLogger(GatewayApp.class);
    private final ServiceConfig config;
    private final NodeHub hub;
    private final BrowserStreams streams;
    private final SnapshotCache snapshots;
    private final ReadRelay reads;
    private final Sessions sessions;
    private final BrowserApi browser;
    private final ClientAddress clientAddress;
    private final List<RateLimiter> limiters;
    private final Health health;
    private final GatewayStateRepository state = new GatewayStateRepository();
    private final SessionRepository sessionData = new SessionRepository();
    private final Clock clock;

    /**
     * @param config the settings
     * @param authenticator checks server keys or tokens
     * @param policy the version policy
     * @param http the HTTP client for Turnstile
     * @param clock the time
     */
    public GatewayApp(
            ServiceConfig config,
            ServerAuthenticator authenticator,
            VersionPolicy policy,
            HttpClient http,
            Clock clock) {
        this.config = config;
        this.clock = clock;
        ServerRepository servers = new ServerRepository();
        IconRepository icons = new IconRepository();
        RateLimiter connects = new RateLimiter(config.connectsPerAddress(), Duration.ofMinutes(1), clock);
        RateLimiter logins = new RateLimiter(config.loginsPerAddress(), Duration.ofMinutes(10), clock);
        RateLimiter lookups = new RateLimiter(60, Duration.ofMinutes(1), clock);
        RateLimiter readLimit = new RateLimiter(config.readsPerSession(), Duration.ofSeconds(10), clock);
        RateLimiter writeLimit = new RateLimiter(config.writesPerSession(), Duration.ofSeconds(10), clock);
        this.health = Health.of(Database::ping, clock);
        this.limiters = List.of(connects, logins, lookups, readLimit, writeLimit);
        this.clientAddress = ClientAddress.trusting(config.trustedProxies(), config.cloudflare());
        this.hub = new NodeHub(authenticator, policy, config.mode().kind(), servers, icons, connects, clock);
        this.streams = new BrowserStreams(clock);
        this.reads = new ReadRelay(clock, config.requestTimeout());
        this.snapshots = new SnapshotCache(hub, streams, reads, clock, config.cacheIdle(), config.requestTimeout());
        this.sessions = new Sessions(
                sessionData,
                hub,
                snapshots,
                reads,
                streams,
                clock,
                new Sessions.Lifetimes(
                        config.publicUrl(),
                        config.sessionIdle(),
                        config.sessionMaxAge(),
                        config.serverGrace(),
                        config.requestTimeout()));
        this.browser = new BrowserApi(
                new BrowserApi.Parts(
                        sessions,
                        hub,
                        snapshots,
                        reads,
                        streams,
                        servers,
                        icons,
                        new Turnstile(config.turnstile(), http),
                        clientAddress),
                new BrowserApi.Limiters(logins, lookups, readLimit, writeLimit),
                new BrowserApi.Settings(
                        config.publicUrl(),
                        config.turnstile().siteKey(),
                        config.secureCookie(),
                        config.sessionMaxAge(),
                        config.requestTimeout(),
                        clock));
        hub.listen(new Relay());
    }

    /**
     * @return the app, not started
     */
    public Javalin create() {
        return Javalin.create(javalin -> {
            javalin.concurrency.useVirtualThreads = true;
            javalin.jsonMapper(new JavalinJackson3(Protocol.mapper(), true));
            javalin.http.maxRequestSize = 64 * 1024;
            javalin.routes.before("/api/*", ctx -> ctx.header("Cache-Control", "no-store"));
            javalin.routes.exception(
                    ApiError.class, (error, ctx) -> ctx.status(error.status()).json(error.view()));
            javalin.routes.get(Routes.HEALTH, this::health);
            javalin.routes.ws(Routes.CONNECT, this::nodes);
            javalin.routes.get(Routes.PENDING, browser::pending);
            javalin.routes.post(Routes.LOGIN, browser::login);
            javalin.routes.get(Routes.SERVER, browser::server);
            javalin.routes.post(Routes.LOGOUT, browser::logout);
            javalin.routes.get(Routes.SNAPSHOT, browser::snapshot);
            javalin.routes.post(Routes.ACTIONS, browser::action);
            javalin.routes.get(Routes.ICON, browser::icon);
            javalin.routes.ws(Routes.STREAM, browser::stream);
        });
    }

    /**
     * Pings nodes, closes silent sockets, ends expired sessions and drops idle snapshots. Called every few seconds.
     */
    public void sweep() {
        run("alive", () -> state.alive(clock.instant()));
        run("node", hub::sweep);
        run("session", sessions::sweep);
        run("snapshot", snapshots::sweep);
        run("read", reads::sweep);
        run("stream", streams::sweep);
        run("rate limit", () -> limiters.forEach(RateLimiter::sweep));
    }

    /**
     * Picks up after a restart: the time since the gateway last ran does not count as idle time of the logins, so a long
     * outage does not log everyone out. Called once at start, before the sweeps.
     */
    public void resume() {
        Instant now = clock.instant();
        state.aliveAt()
                .map(last -> Duration.between(last, now))
                .filter(downtime -> downtime.isPositive())
                .ifPresent(sessionData::excuseIdle);
        state.alive(now);
    }

    /**
     * Closes every websocket, when the gateway stops.
     */
    public void closeAll() {
        hub.closeAll();
        streams.closeAll();
    }

    /**
     * @return the node connections
     */
    public NodeHub hub() {
        return hub;
    }

    /**
     * @return the settings
     */
    public ServiceConfig config() {
        return config;
    }

    private void nodes(WsConfig ws) {
        ws.onConnect(ctx -> {
            ctx.session.setIdleTimeout(Duration.ofSeconds(120));
            hub.connect(ctx.sessionId(), new JettySocket(ctx.session), BrowserApi.address(ctx, clientAddress));
        });
        ws.onMessage(ctx -> hub.message(ctx.sessionId(), ctx.message()));
        ws.onClose(ctx -> hub.disconnect(ctx.sessionId()));
        ws.onError(ctx -> log.debug("Node connection failed", ctx.error()));
    }

    private void health(Context ctx) {
        boolean up = health.up();
        ctx.status(up ? 200 : 503).json(Map.of("status", up ? "up" : "down"));
    }

    private static void run(String what, Runnable sweep) {
        try {
            sweep.run();
        } catch (RuntimeException e) {
            log.error("The {} sweep failed", what, e);
        }
    }

    /**
     * Hands the nodes' messages to the sessions, the snapshot cache and the browser streams.
     */
    private final class Relay implements NodeEvents {
        @Override
        public Message createSession(LiveServer server, NodeLink link, CreateSession request) {
            return sessions.create(server, link, request);
        }

        @Override
        public void sessionClosed(LiveServer server, SessionClosed message) {
            sessions.closedByPlugin(server, message);
        }

        @Override
        public void sessionsRevoked(LiveServer server, SessionsRevoked message) {
            sessions.revokedByPlugin(server, message);
        }

        @Override
        public void sessionsListed(LiveServer server, NodeLink link, SessionsOpen message) {
            sessions.listedByPlugin(server, link, message);
        }

        @Override
        public void presence(LiveServer server, UUID player, boolean online) {
            streams.pushPlayer(server.id(), player, GatewayEvent.presence(online));
        }

        @Override
        public void patch(LiveServer server, NodeLink link, Patch patch) {
            snapshots.patch(server, link, patch);
        }

        @Override
        public void nodeReady(LiveServer server, NodeLink link) {
            snapshots.nodeReady(server, link);
            serverChanged(server);
        }

        @Override
        public void nodeDiscontinued(LiveServer server, NodeLink link) {
            snapshots.nodeDiscontinued(server, link);
        }

        @Override
        public void serverChanged(LiveServer server) {
            streams.pushServer(server.id(), admin -> GatewayEvent.server(browser.serverInfo(server.id(), admin)));
        }

        @Override
        public void serverOffline(LiveServer server) {
            serverChanged(server);
        }
    }
}
