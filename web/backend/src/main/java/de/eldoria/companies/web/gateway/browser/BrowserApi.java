/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.browser;

import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionResult;
import de.eldoria.companies.web.api.handshake.Limits;
import de.eldoria.companies.web.api.handshake.VersionState;
import de.eldoria.companies.web.api.handshake.VersionStatus;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.api.link.Ping;
import de.eldoria.companies.web.api.link.Pong;
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
import de.eldoria.companies.web.gateway.storage.IconRepository;
import de.eldoria.companies.web.gateway.storage.ServerRepository;
import de.eldoria.companies.web.gateway.storage.StoredSession;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The browser's side of the gateway: the login with short id and code, the session cookie, the server's info, the
 * snapshot, read actions and the stream. See {@link de.eldoria.companies.web.gateway.Routes} for the paths.
 * <p>
 * The cookie {@value #COOKIE} is {@code HttpOnly}, {@code Secure} and {@code SameSite=Strict}; the web app never reads
 * it. Logins are limited per client address, read actions per session. The stream accepts only the gateway's own
 * origin and closes with {@value BrowserStreams#CLOSE_SESSION_ENDED} when the session is gone and
 * {@value BrowserStreams#CLOSE_DISCONTINUED} when the server's plugin version is discontinued.
 */
public final class BrowserApi {
    /**
     * The session cookie's name.
     */
    public static final String COOKIE = "companies_session";

    /**
     * The name shown for a server that sent no branding.
     */
    public static final String DEFAULT_NAME = "Minecraft server";

    private static final JsonMapper MAPPER = Protocol.mapper();
    private static final String IMMUTABLE = "public, max-age=31536000, immutable";
    private static final VersionStatus UNKNOWN_VERSION =
            new VersionStatus(VersionState.SUPPORTED, null, null, null, null);
    private static final Limits DEFAULT_LIMITS = new Limits(50);
    private static final long STREAM_MESSAGE_BYTES = 4096;
    private final Sessions sessions;
    private final NodeHub hub;
    private final SnapshotCache snapshots;
    private final ReadRelay reads;
    private final BrowserStreams streams;
    private final ServerRepository servers;
    private final IconRepository icons;
    private final Turnstile turnstile;
    private final ClientAddress clientAddress;
    private final Limiters limits;
    private final Settings settings;

    /**
     * The rate limits of the browser API.
     *
     * @param logins login attempts per client address
     * @param lookups short id lookups per client address
     * @param reads read actions per session
     */
    public record Limiters(RateLimiter logins, RateLimiter lookups, RateLimiter reads) {}

    /**
     * What the browser API needs from the configuration.
     *
     * @param publicUrl the gateway's address, whose origin the stream accepts
     * @param turnstileSiteKey the Turnstile site key, empty when off
     * @param secureCookie whether the cookie is {@code Secure}
     * @param cookieMaxAge the cookie's lifetime
     * @param timeout how long to wait for a node
     * @param clock the time
     */
    public record Settings(
            String publicUrl,
            String turnstileSiteKey,
            boolean secureCookie,
            Duration cookieMaxAge,
            Duration timeout,
            Clock clock) {}

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
    public BrowserApi(Parts parts, Limiters limits, Settings settings) {
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
     * {@code GET /api/v1/s/{shortId}}.
     *
     * @param ctx the request
     */
    public void pending(Context ctx) {
        if (!limits.lookups().tryAcquire(ClientAddress.bucket(address(ctx)))) throw tooMany();
        StoredSession session = sessions.pending(ctx.pathParam("shortId")).orElseThrow(BrowserApi::unknownSession);
        String siteKey = settings.turnstileSiteKey();
        ctx.json(new Views.Pending(
                brand(session.serverId()),
                Optional.ofNullable(session.codeExpires()).orElse(session.created()),
                Math.max(0, Sessions.MAX_ATTEMPTS - session.attempts()),
                siteKey.isBlank() ? null : siteKey,
                version(session.serverId())));
    }

    /**
     * {@code POST /api/v1/s/{shortId}/login}.
     *
     * @param ctx the request
     */
    public void login(Context ctx) {
        String address = address(ctx);
        if (!limits.logins().tryAcquire(ClientAddress.bucket(address))) throw tooMany();
        Views.LoginRequest request = body(ctx, Views.LoginRequest.class);
        String code = request.code();
        if (code == null || code.isBlank()) {
            throw new ApiError(400, ApiError.Code.INTERNAL, "Enter the code from the game");
        }
        if (!turnstile.verify(request.turnstileToken(), address)) {
            throw new ApiError(403, ApiError.Code.TURNSTILE_FAILED, "The browser check failed, try again");
        }
        switch (sessions.login(ctx.pathParam("shortId"), code, Devices.describe(ctx.userAgent()))) {
            case LoginResult.LoggedIn in -> {
                ctx.header("Set-Cookie", cookie(in.cookie(), settings.cookieMaxAge()));
                ctx.status(HttpStatus.NO_CONTENT);
            }
            case LoginResult.WrongCode wrong -> {
                if (wrong.attemptsLeft() == 0) {
                    throw new ApiError(
                            410,
                            ApiError.Code.TOO_MANY_ATTEMPTS,
                            "Too many wrong codes. Run the command in game again for a new one");
                }
                throw new ApiError(401, ApiError.Code.WRONG_CODE, "Wrong code", wrong.attemptsLeft(), null);
            }
            case LoginResult.Unknown ignored -> throw unknownSession();
            case LoginResult.Busy ignored ->
                throw new ApiError(409, ApiError.Code.INTERNAL, "This session is being opened in another browser");
            case LoginResult.Offline ignored -> throw offline();
            case LoginResult.Discontinued discontinued ->
                throw discontinued(
                        discontinued.message(),
                        sessions.pending(ctx.pathParam("shortId"))
                                .map(pending -> version(pending.serverId()))
                                .orElse(null));
            case LoginResult.Refused refused -> throw new ApiError(403, ApiError.Code.UNAUTHORIZED, refused.message());
        }
    }

    /**
     * {@code GET /api/v1/me/server}.
     *
     * @param ctx the request
     */
    public void server(Context ctx) {
        ctx.json(serverInfo(session(ctx).serverId()));
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
     * {@code POST /api/v1/me/actions}.
     *
     * @param ctx the request
     */
    public void action(Context ctx) {
        StoredSession session = session(ctx);
        Views.ActionCall call = body(ctx, Views.ActionCall.class);
        String name = call.name();
        if (name == null || !ReadRelay.READ_ACTIONS.contains(name)) {
            throw new ApiError(403, ApiError.Code.UNAUTHORIZED, "This action is not available on the dashboard");
        }
        if (!limits.reads().tryAcquire(session.id())) throw tooMany();
        LiveServer server = online(session);
        NodeLink link = server.route().orElseThrow(BrowserApi::offline);
        String feature = Actions.feature(name);
        if (feature == null || !link.agreed().features().contains(feature)) {
            throw new ApiError(403, ApiError.Code.UNAUTHORIZED, "The server does not offer this");
        }
        ObjectNode args = Optional.ofNullable(call.args()).orElseGet(MAPPER::createObjectNode);
        ActionResult result = await(session, reads.read(session, link, name, args));
        ctx.json(result);
    }

    /**
     * {@code GET /api/v1/icons/{hash}}.
     *
     * @param ctx the request
     */
    public void icon(Context ctx) {
        String hash = ctx.pathParam("hash").toLowerCase(Locale.ROOT);
        byte[] png = Icons.isHash(hash) ? icons.find(hash).orElse(null) : null;
        if (png == null) throw new ApiError(404, ApiError.Code.INTERNAL, "No such icon");
        ctx.header("Cache-Control", IMMUTABLE)
                .contentType(ContentType.IMAGE_PNG)
                .result(png);
    }

    /**
     * The browser's stream. The gateway sends {@code patch}, {@code pong}, {@code session.closed} and {@code error}
     * messages of the protocol and its own {@link GatewayEvent}s; the browser sends
     * {@code {"type":"ping","v":1,"time":…}} every 20 seconds.
     *
     * @param ws the websocket configuration
     */
    public void stream(WsConfig ws) {
        ws.onConnect(ctx -> {
            ctx.session.setIdleTimeout(Duration.ofSeconds(120));
            ctx.session.setMaxTextMessageSize(STREAM_MESSAGE_BYTES);
            String origin = ctx.header("Origin");
            if (origin != null && !sameOrigin(origin)) {
                ctx.closeSession(1008, "Foreign origin");
                return;
            }
            Optional<StoredSession> session = sessions.authenticate(ctx.cookie(COOKIE));
            if (session.isEmpty()) {
                ctx.closeSession(BrowserStreams.CLOSE_SESSION_ENDED, "Not logged in");
                return;
            }
            StoredSession found = session.get();
            Views.ServerInfo info = serverInfo(found.serverId());
            if (!info.online() && info.version().status() == VersionState.DISCONTINUED) {
                ctx.closeSession(BrowserStreams.CLOSE_DISCONTINUED, "Discontinued");
                return;
            }
            streams.open(ctx.sessionId(), found.id(), found.serverId(), new JettySocket(ctx.session));
            streams.reply(ctx.sessionId(), GatewayEvent.server(info));
        });
        ws.onMessage(ctx -> {
            Message message;
            try {
                message = NodeHub.read(ctx.message());
            } catch (JacksonException e) {
                return;
            }
            if (!(message instanceof Ping ping)) return;
            streams.heard(ctx.sessionId()).ifPresent(sessionId -> {
                sessions.touch(sessionId);
                snapshots.touch(sessionId);
                streams.reply(
                        ctx.sessionId(),
                        new Pong(
                                ping.id(),
                                Protocol.VERSION,
                                ping.time() == null ? settings.clock().instant() : ping.time()));
            });
        });
        ws.onClose(ctx -> streams.closed(ctx.sessionId()));
    }

    /**
     * The server as the browser sees it: branding, the features both the server and the gateway support, the limits
     * and the version status of the node requests go to, and the deprecated parts it uses. While no node is connected,
     * the features are empty and the version reads as supported, unless a node on a discontinued version is connected.
     *
     * @param serverId the server
     * @return the server's info
     */
    public Views.ServerInfo serverInfo(String serverId) {
        Optional<LiveServer> live = hub.server(serverId);
        Optional<NodeLink> routed = live.flatMap(LiveServer::route);
        Optional<NodeLink.Handshake> agreed =
                live.flatMap(LiveServer::representative).map(NodeLink::agreed);
        return new Views.ServerInfo(
                brand(serverId),
                routed.map(link -> link.agreed().features().stream().sorted().toList())
                        .orElse(List.of()),
                routed.map(link -> new Limits(link.maxPageSize())).orElse(DEFAULT_LIMITS),
                agreed.map(NodeLink.Handshake::status).orElse(UNKNOWN_VERSION),
                agreed.map(NodeLink.Handshake::deprecated).orElse(List.of()),
                routed.isPresent());
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

    private Views.Brand brand(String serverId) {
        Optional<ServerRepository.StoredServer> stored = servers.find(serverId);
        String name = stored.map(ServerRepository.StoredServer::name).orElse(DEFAULT_NAME);
        String iconUrl = stored.map(ServerRepository.StoredServer::iconHash)
                .map(hash -> "/api/v1/icons/" + hash)
                .orElse(null);
        return new Views.Brand(name, iconUrl);
    }

    private VersionStatus version(String serverId) {
        return hub.server(serverId)
                .flatMap(LiveServer::representative)
                .map(link -> link.agreed().status())
                .orElse(UNKNOWN_VERSION);
    }

    private StoredSession session(Context ctx) {
        StoredSession session = sessions.authenticate(ctx.cookie(COOKIE))
                .orElseThrow(() -> new ApiError(401, ApiError.Code.UNAUTHORIZED, "Log in with the code from the game"));
        snapshots.touch(session.id());
        return session;
    }

    private LiveServer online(StoredSession session) {
        LiveServer server = hub.server(session.serverId()).orElseThrow(BrowserApi::offline);
        if (server.route().isEmpty()) {
            throw Sessions.discontinued(server)
                    .map(message -> discontinued(message, version(session.serverId())))
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
            throw new ApiError(504, ApiError.Code.SERVER_OFFLINE, "The server did not answer in time");
        } catch (ExecutionException e) {
            NodeRequestException failure = NodeRequestException.of(e);
            if (failure.code() == ErrorCode.UNKNOWN_SESSION) {
                sessions.unknownToPlugin(session);
                throw new ApiError(401, ApiError.Code.UNAUTHORIZED, "The server ended this session");
            }
            if (failure.code() != null) {
                throw new ApiError(502, ApiError.Code.INTERNAL, String.valueOf(failure.getMessage()));
            }
            Optional<String> discontinued = hub.server(session.serverId()).flatMap(Sessions::discontinued);
            if (discontinued.isPresent()) throw discontinued(discontinued.get(), version(session.serverId()));
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
            throw new ApiError(400, ApiError.Code.INTERNAL, "The request body is not valid JSON");
        }
    }

    private static ApiError discontinued(String message, @Nullable VersionStatus version) {
        return new ApiError(410, ApiError.Code.DISCONTINUED, message, null, version);
    }

    private static ApiError unknownSession() {
        return new ApiError(404, ApiError.Code.UNKNOWN_SESSION, "No session is waiting for this link");
    }

    private static ApiError offline() {
        return new ApiError(503, ApiError.Code.SERVER_OFFLINE, "The server is not connected right now");
    }

    private static ApiError tooMany() {
        return new ApiError(429, ApiError.Code.RATE_LIMITED, "Too many requests, wait a moment");
    }
}
