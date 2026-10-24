/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.node;

import de.eldoria.companies.web.api.Features;
import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionResult;
import de.eldoria.companies.web.api.handshake.Branding;
import de.eldoria.companies.web.api.handshake.BrandingUpdate;
import de.eldoria.companies.web.api.handshake.DeprecatedPart;
import de.eldoria.companies.web.api.handshake.GatewayKind;
import de.eldoria.companies.web.api.handshake.Hello;
import de.eldoria.companies.web.api.handshake.VersionState;
import de.eldoria.companies.web.api.handshake.VersionStatus;
import de.eldoria.companies.web.api.handshake.Welcome;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.api.link.ErrorMessage;
import de.eldoria.companies.web.api.link.Ping;
import de.eldoria.companies.web.api.link.Pong;
import de.eldoria.companies.web.api.link.UnknownMessage;
import de.eldoria.companies.web.api.session.CreateSession;
import de.eldoria.companies.web.api.session.SessionClosed;
import de.eldoria.companies.web.api.session.SessionConfirmed;
import de.eldoria.companies.web.api.session.SessionsRevoked;
import de.eldoria.companies.web.api.snapshot.Snapshot;
import de.eldoria.companies.web.api.update.Patch;
import de.eldoria.companies.web.gateway.RateLimiter;
import de.eldoria.companies.web.gateway.Socket;
import de.eldoria.companies.web.gateway.policy.VersionPolicy;
import de.eldoria.companies.web.gateway.storage.IconRepository;
import de.eldoria.companies.web.gateway.storage.ServerRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The connections of the nodes on {@code /api/v1/servers/connect}.
 * <p>
 * A node's first message is its {@code hello}. The gateway checks its key or token, looks up its plugin version in
 * the {@link VersionPolicy}, stores the server's branding icon by hash and answers {@code welcome} with the protocol
 * version used, the gateway's kind and features, the version status and the deprecated parts the node still uses. A
 * node on a discontinued version gets its welcome and then only answers to pings. Every node of a server connects;
 * a node that connects again replaces its previous connection.
 * <p>
 * The gateway pings a node it has not heard from for {@value #PING_SECONDS} seconds and closes a connection silent
 * for {@value #IDLE_SECONDS} seconds, or one that sends no hello within {@value #HELLO_SECONDS} seconds, or anything
 * else first. Until its welcome a node may send messages of up to {@value #HELLO_BYTES} bytes, enough for a hello with
 * the largest icon; afterwards {@value #MESSAGE_BYTES} bytes.
 */
public final class NodeHub {
    /**
     * Close code for a message that cannot be read or does not belong where it came.
     */
    public static final int CLOSE_INVALID = 4400;

    /**
     * Close code for a refused key or token.
     */
    public static final int CLOSE_UNAUTHORIZED = 4401;

    /**
     * Close code for a silent connection.
     */
    public static final int CLOSE_IDLE = 4408;

    /**
     * Close code for a connection replaced by a newer one of the same node.
     */
    public static final int CLOSE_REPLACED = 4409;

    /**
     * Close code for an address opening too many connections.
     */
    public static final int CLOSE_RATE_LIMITED = 4429;

    private static final Logger log = LoggerFactory.getLogger(NodeHub.class);
    private static final JsonMapper MAPPER = Protocol.mapper();
    private static final long HELLO_SECONDS = 10;
    private static final long HELLO_BYTES = 128L * 1024;
    private static final long MESSAGE_BYTES = 4L * 1024 * 1024;
    private static final long PING_SECONDS = 30;
    private static final long IDLE_SECONDS = 75;
    private final Map<String, NodeLink> links = new ConcurrentHashMap<>();
    private final Map<String, LiveServer> servers = new ConcurrentHashMap<>();
    private final ServerAuthenticator authenticator;
    private final VersionPolicy policy;
    private final GatewayKind kind;
    private final ServerRepository serverRepository;
    private final IconRepository icons;
    private final RateLimiter connects;
    private final Clock clock;
    private volatile NodeEvents events = new Unlistened();

    /**
     * @param authenticator checks keys or tokens
     * @param policy the version policy
     * @param kind hosted or self-hosted
     * @param serverRepository the stored servers
     * @param icons the stored icons
     * @param connects limits connections per address
     * @param clock the time
     */
    public NodeHub(
            ServerAuthenticator authenticator,
            VersionPolicy policy,
            GatewayKind kind,
            ServerRepository serverRepository,
            IconRepository icons,
            RateLimiter connects,
            Clock clock) {
        this.authenticator = authenticator;
        this.policy = policy;
        this.kind = kind;
        this.serverRepository = serverRepository;
        this.icons = icons;
        this.connects = connects;
        this.clock = clock;
    }

    /**
     * @param events where node messages go from now on
     */
    public void listen(NodeEvents events) {
        this.events = events;
    }

    /**
     * @param id a server id
     * @return the server while a node of it is welcomed
     */
    public Optional<LiveServer> server(String id) {
        return Optional.ofNullable(servers.get(id));
    }

    /**
     * @return the features this gateway offers today: every feature of the protocol except those past their
     * deprecation window
     */
    public List<String> features() {
        Set<String> removed = policy.removedFeatures(today());
        return Features.ALL.stream()
                .filter(feature -> !removed.contains(feature))
                .toList();
    }

    /**
     * Reads a node's message the way the gateway does: leniently, with an unknown type as {@link UnknownMessage}.
     *
     * @param text the message
     * @return the message
     * @throws JacksonException when the text is no message
     */
    public static Message read(String text) {
        return MAPPER.readValue(text, Message.class);
    }

    /**
     * A node opened a connection.
     *
     * @param socketId the websocket's id
     * @param socket the websocket
     * @param address the client address
     */
    public void connect(String socketId, Socket socket, String address) {
        NodeLink link = new NodeLink(socketId, socket, address, clock.instant());
        socket.limit(HELLO_BYTES);
        if (!connects.tryAcquire(address)) {
            socket.close(CLOSE_RATE_LIMITED, "Too many connections, try again later");
            return;
        }
        links.put(socketId, link);
    }

    /**
     * A node sent a message.
     *
     * @param socketId the websocket's id
     * @param text the message
     */
    public void message(String socketId, String text) {
        NodeLink link = links.get(socketId);
        if (link == null) return;
        link.heard(clock.instant());
        Message message;
        try {
            message = read(text);
        } catch (JacksonException e) {
            link.send(new ErrorMessage(null, Protocol.VERSION, ErrorCode.INVALID, "Unreadable message"));
            return;
        }
        try {
            switch (link.state()) {
                case AWAITING_HELLO -> awaitingHello(link, message);
                case DISCONTINUED -> discontinued(link, message);
                case READY -> ready(link, message);
                case CLOSED -> {}
            }
        } catch (RuntimeException e) {
            log.error("Handling a {} failed", message.getClass().getSimpleName(), e);
            link.send(new ErrorMessage(message.id(), Protocol.VERSION, ErrorCode.INTERNAL, "The gateway failed"));
        }
    }

    /**
     * A node's connection closed.
     *
     * @param socketId the websocket's id
     */
    public void disconnect(String socketId) {
        NodeLink link = links.remove(socketId);
        if (link == null) return;
        link.closed();
        NodeLink.Handshake agreed = link.handshake();
        if (agreed == null) return;
        LiveServer offline = null;
        synchronized (this) {
            LiveServer server = servers.get(agreed.serverId());
            if (server != null && server.remove(link) && server.isEmpty()) {
                servers.remove(server.id());
                serverRepository.disconnected(server.id(), clock.instant());
                offline = server;
            }
        }
        if (offline != null) events.serverOffline(offline);
    }

    /**
     * Pings quiet nodes and closes silent connections. Called every few seconds.
     */
    public void sweep() {
        Instant now = clock.instant();
        for (NodeLink link : List.copyOf(links.values())) {
            try {
                sweep(link, now);
            } catch (RuntimeException e) {
                log.warn("Sweeping a node connection failed", e);
            }
        }
    }

    private void sweep(NodeLink link, Instant now) {
        NodeLink.State state = link.state();
        if (state == NodeLink.State.CLOSED) {
            if (link.lastHeard().isBefore(now.minusSeconds(IDLE_SECONDS))) disconnect(link.socketId());
            return;
        }
        if (state == NodeLink.State.AWAITING_HELLO) {
            if (link.connected().isBefore(now.minusSeconds(HELLO_SECONDS))) link.close(CLOSE_IDLE, "No hello");
            return;
        }
        NodeLink.Handshake agreed = link.handshake();
        if (agreed == null) return;
        if (link.lastHeard().isBefore(now.minusSeconds(IDLE_SECONDS))) {
            link.close(CLOSE_IDLE, "Idle");
        } else if (link.lastHeard().isBefore(now.minusSeconds(PING_SECONDS))) {
            link.send(new Ping(link.nextId(), agreed.protocol(), now));
        }
    }

    /**
     * Closes every connection, when the gateway stops.
     */
    public void closeAll() {
        for (NodeLink link : List.copyOf(links.values())) link.close(1001, "The gateway is stopping");
    }

    private void awaitingHello(NodeLink link, Message message) {
        if (!(message instanceof Hello hello)) {
            link.send(error(message, ErrorCode.UNAUTHORIZED, "Send hello first"));
            link.close(CLOSE_INVALID, "No hello");
            return;
        }
        if (hello.credentials() == null
                || hello.node() == null
                || hello.node().uid() == null
                || hello.node().role() == null
                || hello.credentials().kind() == null
                || hello.credentials().key() == null
                || hello.plugin() == null) {
            link.send(error(message, ErrorCode.INVALID, "The hello misses credentials, node or plugin"));
            link.close(CLOSE_INVALID, "Invalid hello");
            return;
        }
        if (hello.v() < 1) {
            link.send(error(message, ErrorCode.UNSUPPORTED_PROTOCOL, "Protocol 1 or newer is needed"));
            link.close(CLOSE_INVALID, "Unsupported protocol");
            return;
        }
        switch (authenticator.authenticate(hello.credentials())) {
            case Authentication.Refused refused -> {
                link.send(error(message, refused.code(), refused.reason()));
                link.close(CLOSE_UNAUTHORIZED, "Unauthorized");
            }
            case Authentication.Accepted accepted -> welcome(link, hello, accepted);
        }
    }

    private void welcome(NodeLink link, Hello hello, Authentication.Accepted accepted) {
        Instant now = clock.instant();
        LocalDate today = today();
        int protocol = Math.min(hello.v(), Protocol.VERSION);
        List<String> offered = features();
        List<String> listed = hello.features() == null ? List.of() : hello.features();
        Set<String> features = listed.stream().filter(offered::contains).collect(Collectors.toUnmodifiableSet());
        VersionStatus status = policy.status(hello.plugin(), accepted.keyKind(), today);
        List<DeprecatedPart> deprecated = policy.inUse(hello.plugin(), listed, protocol, today);
        NodeLink.Handshake agreed =
                new NodeLink.Handshake(accepted.serverId(), hello, protocol, features, status, deprecated);
        boolean discontinued = status.status() == VersionState.DISCONTINUED;
        LiveServer server;
        NodeLink replaced;
        synchronized (this) {
            if (!link.welcomed(agreed, discontinued ? NodeLink.State.DISCONTINUED : NodeLink.State.READY)) return;
            serverRepository.connected(accepted.serverId(), now);
            serverRepository.node(
                    accepted.serverId(), agreed.uid(), agreed.role().name(), hello.plugin(), protocol, now);
            server = servers.computeIfAbsent(accepted.serverId(), LiveServer::new);
            replaced = server.add(link);
        }
        if (replaced != null && replaced != link) replaced.close(CLOSE_REPLACED, "Replaced by a newer connection");
        link.limit(MESSAGE_BYTES);
        boolean iconCached = branding(accepted.serverId(), hello.branding());
        link.send(new Welcome(hello.id(), protocol, protocol, kind, offered, status, deprecated, iconCached));
        log.info(
                "Node {} ({}) of {} connected with Companies {}, {}",
                agreed.uid(),
                agreed.role(),
                accepted.serverId(),
                hello.plugin(),
                status.status());
        if (discontinued) events.serverChanged(server);
        else events.nodeReady(server, link);
    }

    private void discontinued(NodeLink link, Message message) {
        switch (message) {
            case Ping ping -> link.send(new Pong(ping.id(), link.agreed().protocol(), ping.time()));
            case Pong ignored -> {}
            default ->
                link.send(error(
                        message,
                        ErrorCode.DISCONTINUED,
                        "This plugin version is discontinued on the dashboard; update Companies"));
        }
    }

    private void ready(NodeLink link, Message message) {
        LiveServer server = servers.get(link.agreed().serverId());
        if (server == null) return;
        int v = link.agreed().protocol();
        switch (message) {
            case Ping ping -> link.send(new Pong(ping.id(), v, ping.time()));
            case Pong ignored -> {}
            case BrandingUpdate update -> {
                branding(server.id(), update.branding());
                events.serverChanged(server);
            }
            case CreateSession request -> link.send(events.createSession(server, link, request));
            case SessionClosed closed -> events.sessionClosed(server, closed);
            case SessionsRevoked revoked -> events.sessionsRevoked(server, revoked);
            case Patch patch -> events.patch(server, link, patch);
            case SessionConfirmed confirmed -> answer(link, confirmed);
            case Snapshot snapshot -> answer(link, snapshot);
            case ActionResult result -> answer(link, result);
            case ErrorMessage error -> {
                if (!link.answer(error))
                    log.debug("Node {} reported {}", link.agreed().uid(), error.code());
            }
            case UnknownMessage unknown -> link.send(error(unknown, ErrorCode.UNKNOWN_TYPE, "Unknown message type"));
            default -> link.send(error(message, ErrorCode.INVALID, "Not a message for the gateway"));
        }
    }

    private void answer(NodeLink link, Message answer) {
        if (!link.answer(answer)) link.send(error(answer, ErrorCode.INVALID, "No request with this id"));
    }

    private boolean branding(String serverId, @Nullable Branding branding) {
        if (branding == null) {
            serverRepository.branding(serverId, null, null);
            return false;
        }
        String name = branding.name() == null ? null : truncate(branding.name());
        String hash = branding.iconHash();
        String icon = branding.icon();
        if (hash == null || !Icons.isHash(hash)) {
            serverRepository.branding(serverId, name, null);
            return false;
        }
        if (icon != null) Icons.decode(icon, hash).ifPresent(png -> icons.store(hash, png));
        boolean cached = icons.exists(hash);
        serverRepository.branding(serverId, name, cached ? hash : null);
        return cached;
    }

    private static String truncate(String name) {
        String stripped = name.strip();
        return stripped.length() <= Branding.MAX_NAME_LENGTH
                ? stripped
                : stripped.substring(0, Branding.MAX_NAME_LENGTH);
    }

    private static ErrorMessage error(Message refused, ErrorCode code, String message) {
        return new ErrorMessage(refused.id(), Protocol.VERSION, code, message);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * Refuses sessions and drops everything else until {@link #listen(NodeEvents)} is called.
     */
    private static final class Unlistened implements NodeEvents {
        @Override
        public Message createSession(LiveServer server, NodeLink link, CreateSession request) {
            return new ErrorMessage(request.id(), Protocol.VERSION, ErrorCode.INTERNAL, "The gateway is starting");
        }

        @Override
        public void sessionClosed(LiveServer server, SessionClosed message) {}

        @Override
        public void sessionsRevoked(LiveServer server, SessionsRevoked message) {}

        @Override
        public void patch(LiveServer server, NodeLink link, Patch patch) {}

        @Override
        public void nodeReady(LiveServer server, NodeLink link) {}

        @Override
        public void serverChanged(LiveServer server) {}

        @Override
        public void serverOffline(LiveServer server) {}
    }

    /**
     * @return how long the gateway lets a node stay silent
     */
    public static Duration idleTimeout() {
        return Duration.ofSeconds(IDLE_SECONDS);
    }
}
