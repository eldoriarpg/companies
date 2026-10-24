/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.browser;

import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionResult;
import de.eldoria.companies.web.api.handshake.GatewayKind;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.api.session.PlayerRef;
import de.eldoria.companies.web.gateway.ClientAddress;
import de.eldoria.companies.web.gateway.JettySocket;
import de.eldoria.companies.web.gateway.RateLimiter;
import de.eldoria.companies.web.gateway.node.Icons;
import de.eldoria.companies.web.gateway.node.LiveServer;
import de.eldoria.companies.web.gateway.node.NodeHub;
import de.eldoria.companies.web.gateway.node.NodeLink;
import de.eldoria.companies.web.gateway.node.NodeRequestException;
import de.eldoria.companies.web.gateway.session.Devices;
import de.eldoria.companies.web.gateway.session.LoginResult;
import de.eldoria.companies.web.gateway.session.Sessions;
import de.eldoria.companies.web.gateway.session.Turnstile;
import de.eldoria.companies.web.gateway.snapshot.ReadRelay;
import de.eldoria.companies.web.gateway.snapshot.SnapshotCache;
import de.eldoria.companies.web.gateway.snapshot.SnapshotView;
import de.eldoria.companies.web.gateway.storage.IconRepository;
import de.eldoria.companies.web.gateway.storage.ServerRepository;
import de.eldoria.companies.web.gateway.storage.StoredSession;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The browser's side of the gateway: the login with short id and code, the session cookie, the snapshot, read
 * requests and the stream. See {@link de.eldoria.companies.web.gateway.Routes} for the paths.
 * <p>
 * The cookie {@value #COOKIE} is {@code HttpOnly}, {@code Secure} and {@code SameSite=Strict}; the web app never reads
 * it. Logins are limited per client address, read requests per session. The stream accepts only the gateway's own
 * origin.
 */
public final class BrowserApi {
    /**
     * The session cookie's name.
     */
    public static final String COOKIE = "companies_session";

    private static final JsonMapper MAPPER = Protocol.mapper();
    private static final String IMMUTABLE = "public, max-age=31536000, immutable";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private final Sessions sessions;
    private final NodeHub hub;
    private final SnapshotCache snapshots;
    private final ReadRelay reads;
    private final BrowserStreams streams;
    private final ServerRepository servers;
    private final IconRepository icons;
    private final Turnstile turnstile;
    private final ClientAddress clientAddress;
    private final Limits limits;
    private final Settings settings;

    /**
     * The rate limits of the browser API.
     *
     * @param logins login attempts per client address
     * @param lookups short id lookups per client address
     * @param reads read requests per session
     */
    public record Limits(RateLimiter logins, RateLimiter lookups, RateLimiter reads) {}

    /**
     * What the browser API needs from the configuration.
     *
     * @param gateway hosted or self-hosted
     * @param publicUrl the gateway's address, whose origin the stream accepts
     * @param turnstileSiteKey the Turnstile site key, empty when off
     * @param secureCookie whether the cookie is {@code Secure}
     * @param cookieMaxAge the cookie's lifetime
     * @param timeout how long to wait for a node
     */
    public record Settings(
            GatewayKind gateway,
            String publicUrl,
            String turnstileSiteKey,
            boolean secureCookie,
            Duration cookieMaxAge,
            Duration timeout) {}

    /**
     * The parts the browser API works with.
     *
     * @param sessions the sessions
     * @param hub the node connections
     * @param snapshots the snapshot cache
     * @param reads the read relay
     * @param streams the browser streams
     * @param servers the stored servers
     * @param icons the stored icons
     * @param turnstile the login challenge
     * @param clientAddress resolves client addresses
     */
    public record Parts(
            Sessions sessions,
            NodeHub hub,
            SnapshotCache snapshots,
            ReadRelay reads,
            BrowserStreams streams,
            ServerRepository servers,
            IconRepository icons,
            Turnstile turnstile,
            ClientAddress clientAddress) {}

    /**
     * @param parts the parts
     * @param limits the rate limits
     * @param settings the settings
     */
    public BrowserApi(Parts parts, Limits limits, Settings settings) {
        this.sessions = parts.sessions();
        this.hub = parts.hub();
        this.snapshots = parts.snapshots();
        this.reads = parts.reads();
        this.streams = parts.streams();
        this.servers = parts.servers();
        this.icons = parts.icons();
        this.turnstile = parts.turnstile();
        this.clientAddress = parts.clientAddress();
        this.limits = limits;
        this.settings = settings;
    }

    /**
     * {@code GET /api/v1/config}.
     *
     * @param ctx the request
     */
    public void config(Context ctx) {
        String siteKey = settings.turnstileSiteKey();
        ctx.json(new Views.ConfigView(settings.gateway(), siteKey.isBlank() ? null : siteKey));
    }

    /**
     * {@code GET /api/v1/s/{shortId}}.
     *
     * @param ctx the request
     */
    public void pending(Context ctx) {
        if (!limits.lookups().tryAcquire(address(ctx))) throw tooMany();
        StoredSession session = sessions.pending(ctx.pathParam("shortId"))
                .orElseThrow(() -> new ApiError(404, "unknown-session", "No session is waiting for this link"));
        ctx.json(new Views.PendingView(
                Optional.ofNullable(session.shortId()).orElse(""),
                server(session.serverId()),
                Optional.ofNullable(session.codeExpires()).orElse(session.created())));
    }

    /**
     * {@code POST /api/v1/s/{shortId}/login}.
     *
     * @param ctx the request
     */
    public void login(Context ctx) {
        String address = address(ctx);
        if (!limits.logins().tryAcquire(address)) throw tooMany();
        Views.LoginRequest request = body(ctx, Views.LoginRequest.class);
        String code = request.code();
        if (code == null || code.isBlank()) throw new ApiError(400, "missing-code", "Enter the code from the game");
        if (!turnstile.verify(request.turnstile(), address)) {
            throw new ApiError(400, "challenge-failed", "The browser check failed, try again");
        }
        switch (sessions.login(ctx.pathParam("shortId"), code, Devices.describe(ctx.userAgent()))) {
            case LoginResult.LoggedIn in -> {
                ctx.header("Set-Cookie", cookie(in.cookie(), settings.cookieMaxAge()));
                ctx.json(me(in.session()));
            }
            case LoginResult.WrongCode wrong ->
                throw new ApiError(
                        401,
                        "wrong-code",
                        wrong.attemptsLeft() == 0
                                ? "Wrong code. Run the command in game again for a new one"
                                : "Wrong code",
                        wrong.attemptsLeft());
            case LoginResult.Unknown ignored ->
                throw new ApiError(404, "unknown-session", "No session is waiting for this link");
            case LoginResult.Busy ignored ->
                throw new ApiError(409, "busy", "This session is being opened in another browser");
            case LoginResult.Offline ignored ->
                throw new ApiError(503, "server-offline", "The server is not connected right now");
            case LoginResult.Discontinued discontinued ->
                throw new ApiError(410, "discontinued", discontinued.message());
            case LoginResult.Refused refused -> throw new ApiError(403, "refused", refused.message());
        }
    }

    /**
     * {@code GET /api/v1/me}.
     *
     * @param ctx the request
     */
    public void me(Context ctx) {
        ctx.json(me(session(ctx)));
    }

    /**
     * {@code POST /api/v1/me/logout}.
     *
     * @param ctx the request
     */
    public void logout(Context ctx) {
        sessions.authenticate(ctx.cookie(COOKIE)).ifPresent(sessions::logout);
        ctx.header("Set-Cookie", cookie("", Duration.ZERO));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * {@code GET /api/v1/me/snapshot}.
     *
     * @param ctx the request
     */
    public void snapshot(Context ctx) {
        StoredSession session = session(ctx);
        ctx.json(await(session, snapshots.snapshot(session)));
    }

    /**
     * {@code GET /api/v1/me/members?page&size&search}.
     *
     * @param ctx the request
     */
    public void members(Context ctx) {
        StoredSession session = session(ctx);
        SnapshotView view = await(session, snapshots.snapshot(session));
        String search = Optional.ofNullable(ctx.queryParam("search"))
                .map(text -> text.strip().toLowerCase(Locale.ROOT))
                .orElse("");
        List<JsonNode> members = view.snapshot()
                .path("company")
                .path("members")
                .valueStream()
                .filter(member -> member.path("name")
                        .asString("")
                        .toLowerCase(Locale.ROOT)
                        .contains(search))
                .toList();
        int size = Math.clamp(integer(ctx, "size", DEFAULT_PAGE_SIZE), 1, MAX_PAGE_SIZE);
        int pages = Math.max(1, (members.size() + size - 1) / size);
        int page = Math.clamp(integer(ctx, "page", 1), 1, pages);
        int from = (page - 1) * size;
        List<JsonNode> entries = members.subList(Math.min(from, members.size()), Math.min(from + size, members.size()));
        ctx.json(new Views.PageView(page, pages, members.size(), entries));
    }

    /**
     * {@code POST /api/v1/me/actions/{name}}.
     *
     * @param ctx the request
     */
    public void action(Context ctx) {
        StoredSession session = session(ctx);
        String name = ctx.pathParam("name");
        if (!ReadRelay.READ_ACTIONS.contains(name)) {
            throw new ApiError(403, "not-available", "This action is not available on the dashboard yet");
        }
        if (!limits.reads().tryAcquire(session.id())) throw tooMany();
        LiveServer server = online(session);
        NodeLink link = server.route().orElseThrow(BrowserApi::offline);
        String feature = Actions.feature(name);
        if (feature == null || !link.agreed().features().contains(feature)) {
            throw new ApiError(404, "feature-unavailable", "The server does not offer this");
        }
        ObjectNode args = ctx.body().isBlank() ? MAPPER.createObjectNode() : body(ctx, ObjectNode.class);
        snapshots.touch(session.id());
        ActionResult result = await(session, reads.read(session, link, name, args));
        ctx.json(new Views.ActionView(result.ok(), result.reason(), result.message(), result.data()));
    }

    /**
     * {@code GET /api/v1/icons/{hash}}.
     *
     * @param ctx the request
     */
    public void icon(Context ctx) {
        String hash = ctx.pathParam("hash").toLowerCase(Locale.ROOT);
        byte[] png = Icons.isHash(hash) ? icons.find(hash).orElse(null) : null;
        if (png == null) throw new ApiError(404, "unknown-icon", "No such icon");
        ctx.header("Cache-Control", IMMUTABLE)
                .contentType(ContentType.IMAGE_PNG)
                .result(png);
    }

    /**
     * The browser's stream: patches and session events from the gateway, {@code {"type":"ping"}} from the browser.
     *
     * @param ws the websocket configuration
     */
    public void stream(WsConfig ws) {
        ws.onConnect(ctx -> {
            ctx.session.setIdleTimeout(Duration.ofSeconds(120));
            String origin = ctx.header("Origin");
            if (origin != null && !sameOrigin(origin)) {
                ctx.closeSession(4403, "Foreign origin");
                return;
            }
            Optional<StoredSession> session = sessions.authenticate(ctx.cookie(COOKIE));
            if (session.isEmpty()) {
                ctx.closeSession(BrowserStreams.CLOSE_SESSION_ENDED, "Not logged in");
                return;
            }
            StoredSession found = session.get();
            streams.open(ctx.sessionId(), found.id(), found.serverId(), new JettySocket(ctx.session));
            boolean online =
                    hub.server(found.serverId()).flatMap(LiveServer::route).isPresent();
            streams.reply(ctx.sessionId(), StreamEvent.server(online));
        });
        ws.onMessage(ctx -> {
            if (!ctx.message().contains("ping")) return;
            streams.heard(ctx.sessionId()).ifPresent(sessionId -> {
                sessions.touch(sessionId);
                snapshots.touch(sessionId);
                streams.reply(ctx.sessionId(), StreamEvent.pong());
            });
        });
        ws.onClose(ctx -> streams.closed(ctx.sessionId()));
    }

    /**
     * @param ctx a websocket
     * @param proxies resolves the client address
     * @return the client address of the websocket
     */
    public static String address(WsContext ctx, ClientAddress proxies) {
        SocketAddress remote = ctx.session.getRemoteSocketAddress();
        String socket = remote instanceof InetSocketAddress inet && inet.getAddress() != null
                ? inet.getAddress().getHostAddress()
                : String.valueOf(remote);
        return proxies.of(socket, ctx.header("X-Forwarded-For"), ctx.header("CF-Connecting-IP"));
    }

    private Views.MeView me(StoredSession session) {
        List<String> features = hub.server(session.serverId())
                .flatMap(LiveServer::route)
                .map(link -> link.agreed().features().stream().sorted().toList())
                .orElse(List.of());
        return new Views.MeView(
                new PlayerRef(session.player(), session.playerName()),
                server(session.serverId()),
                features,
                sessions.expires(session));
    }

    private Views.ServerView server(String serverId) {
        Optional<ServerRepository.StoredServer> stored = servers.find(serverId);
        String name = stored.map(ServerRepository.StoredServer::name).orElse(null);
        String icon = stored.map(ServerRepository.StoredServer::iconHash)
                .map(hash -> "/api/v1/icons/" + hash)
                .orElse(null);
        Optional<LiveServer> live = hub.server(serverId);
        boolean online = live.flatMap(LiveServer::route).isPresent();
        Optional<NodeLink.Handshake> agreed =
                live.flatMap(LiveServer::representative).map(NodeLink::agreed);
        return new Views.ServerView(
                name,
                icon,
                online,
                agreed.map(NodeLink.Handshake::status).orElse(null),
                agreed.map(NodeLink.Handshake::deprecated).orElse(List.of()));
    }

    private StoredSession session(Context ctx) {
        StoredSession session = sessions.authenticate(ctx.cookie(COOKIE))
                .orElseThrow(() -> new ApiError(401, "not-logged-in", "Log in with the code from the game"));
        snapshots.touch(session.id());
        return session;
    }

    private LiveServer online(StoredSession session) {
        LiveServer server = hub.server(session.serverId()).orElseThrow(BrowserApi::offline);
        if (server.route().isEmpty()) {
            throw Sessions.discontinued(server)
                    .map(message -> new ApiError(410, "discontinued", message))
                    .orElseGet(BrowserApi::offline);
        }
        return server;
    }

    private <T> T await(StoredSession session, CompletableFuture<T> answer) {
        try {
            return answer.get(settings.timeout().toMillis() + 1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw offline();
        } catch (TimeoutException e) {
            throw new ApiError(504, "server-timeout", "The server did not answer in time");
        } catch (ExecutionException e) {
            NodeRequestException failure = NodeRequestException.of(e);
            if (failure.code() == ErrorCode.UNKNOWN_SESSION) {
                sessions.unknownToPlugin(session);
                throw new ApiError(401, "not-logged-in", "The server ended this session");
            }
            if (failure.code() != null) throw new ApiError(502, "server-error", String.valueOf(failure.getMessage()));
            Optional<String> discontinued = hub.server(session.serverId()).flatMap(Sessions::discontinued);
            if (discontinued.isPresent()) throw new ApiError(410, "discontinued", discontinued.get());
            throw offline();
        }
    }

    private boolean sameOrigin(String origin) {
        URI own = URI.create(settings.publicUrl());
        return origin.equalsIgnoreCase(own.getScheme() + "://" + own.getRawAuthority());
    }

    private String cookie(String value, Duration maxAge) {
        return COOKIE + "=" + value + "; Path=/; Max-Age=" + maxAge.toSeconds() + "; HttpOnly; SameSite=Strict"
                + (settings.secureCookie() ? "; Secure" : "");
    }

    private String address(Context ctx) {
        return clientAddress.of(ctx.ip(), ctx.header("X-Forwarded-For"), ctx.header("CF-Connecting-IP"));
    }

    private static <T> T body(Context ctx, Class<T> type) {
        try {
            return MAPPER.readValue(ctx.body(), type);
        } catch (JacksonException e) {
            throw new ApiError(400, "invalid-body", "The request body is not valid JSON");
        }
    }

    private static int integer(Context ctx, String name, int fallback) {
        String value = ctx.queryParam(name);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ApiError(400, "invalid-query", name + " must be a number");
        }
    }

    private static ApiError offline() {
        return new ApiError(503, "server-offline", "The server is not connected right now");
    }

    private static ApiError tooMany() {
        return new ApiError(429, "rate-limited", "Too many requests, wait a moment");
    }
}
