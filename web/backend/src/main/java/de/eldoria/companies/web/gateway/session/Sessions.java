/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.session;

import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.handshake.VersionState;
import de.eldoria.companies.web.api.handshake.VersionStatus;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.api.link.ErrorMessage;
import de.eldoria.companies.web.api.session.CloseReason;
import de.eldoria.companies.web.api.session.CreateSession;
import de.eldoria.companies.web.api.session.SessionClosed;
import de.eldoria.companies.web.api.session.SessionConfirmed;
import de.eldoria.companies.web.api.session.SessionCreated;
import de.eldoria.companies.web.api.session.SessionOpened;
import de.eldoria.companies.web.api.session.SessionsRevoked;
import de.eldoria.companies.web.gateway.Secrets;
import de.eldoria.companies.web.gateway.browser.BrowserStreams;
import de.eldoria.companies.web.gateway.node.LiveServer;
import de.eldoria.companies.web.gateway.node.NodeHub;
import de.eldoria.companies.web.gateway.node.NodeLink;
import de.eldoria.companies.web.gateway.node.NodeRequestException;
import de.eldoria.companies.web.gateway.snapshot.ReadRelay;
import de.eldoria.companies.web.gateway.snapshot.SnapshotCache;
import de.eldoria.companies.web.gateway.storage.SessionRepository;
import de.eldoria.companies.web.gateway.storage.SessionState;
import de.eldoria.companies.web.gateway.storage.StoredSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * Dashboard sessions from the player's command to the end.
 * <ol>
 *     <li>A node asks for a session for a player ({@code session.create}). The gateway creates a pending session with
 *     a short id of six characters of an unambiguous alphabet and a six-letter code without look-alikes, valid for
 *     {@value #CODE_MINUTES} minutes and {@value #MAX_ATTEMPTS} attempts, and answers {@code session.created} with
 *     the link.</li>
 *     <li>The browser enters the code. It is compared in constant time against its stored hash; a wrong code counts
 *     an attempt, and the last attempt ends the session. The right code sends {@code session.opened} to the node the
 *     player asked on (any ready node when it is gone), and only the plugin's {@code session.confirmed} binds the
 *     browser through a new cookie value, stored as its hash.</li>
 *     <li>The session ends on logout, when it is idle or too old, when its server has been gone longer than the grace
 *     period, or when the plugin closes or revokes it. The gateway tells a node when it ends a session itself.</li>
 * </ol>
 * Sessions live in the database, so a restart of the gateway keeps players logged in.
 */
public final class Sessions {
    /**
     * How many codes may be tried per session.
     */
    public static final int MAX_ATTEMPTS = 5;

    /**
     * How long a code works.
     */
    public static final long CODE_MINUTES = 5;

    private static final Logger log = LoggerFactory.getLogger(Sessions.class);
    private static final int CREATE_TRIES = 10;
    private static final Duration TOUCH_INTERVAL = Duration.ofSeconds(30);
    private final SessionRepository repository;
    private final NodeHub hub;
    private final SnapshotCache snapshots;
    private final ReadRelay reads;
    private final BrowserStreams streams;
    private final Clock clock;
    private final String publicUrl;
    private final Duration idle;
    private final Duration maxAge;
    private final Duration grace;
    private final Duration timeout;

    /**
     * The lifetimes of sessions.
     *
     * @param publicUrl the address links start with
     * @param idle how long a session lives without being used
     * @param maxAge how long a session lives at most
     * @param grace how long sessions survive their server being gone
     * @param timeout how long to wait for the plugin to confirm a login
     */
    public record Lifetimes(String publicUrl, Duration idle, Duration maxAge, Duration grace, Duration timeout) {}

    /**
     * @param repository the stored sessions
     * @param hub the node connections
     * @param snapshots the snapshot cache
     * @param reads the read cache
     * @param streams the browser streams
     * @param clock the time
     * @param lifetimes the lifetimes
     */
    public Sessions(
            SessionRepository repository,
            NodeHub hub,
            SnapshotCache snapshots,
            ReadRelay reads,
            BrowserStreams streams,
            Clock clock,
            Lifetimes lifetimes) {
        this.repository = repository;
        this.hub = hub;
        this.snapshots = snapshots;
        this.reads = reads;
        this.streams = streams;
        this.clock = clock;
        this.publicUrl = lifetimes.publicUrl();
        this.idle = lifetimes.idle();
        this.maxAge = lifetimes.maxAge();
        this.grace = lifetimes.grace();
        this.timeout = lifetimes.timeout();
    }

    /**
     * Creates a pending session for a player who ran {@code /company dashboard}.
     *
     * @param server the player's server
     * @param link the node that asked
     * @param request the request
     * @return {@code session.created}, or an error for an incomplete request
     */
    public Message create(LiveServer server, NodeLink link, CreateSession request) {
        int v = link.agreed().protocol();
        if (request.player() == null
                || request.player().uuid() == null
                || request.player().name() == null) {
            return new ErrorMessage(request.id(), v, ErrorCode.INVALID, "The request names no player");
        }
        UUID node = request.node() == null ? link.agreed().uid() : request.node();
        Instant now = clock.instant();
        Instant expires = now.plus(Duration.ofMinutes(CODE_MINUTES));
        for (int i = 0; i < CREATE_TRIES; i++) {
            String id = Secrets.sessionId();
            String shortId = Secrets.shortId();
            String code = Secrets.code();
            boolean created = repository.createPending(
                    id,
                    server.id(),
                    request.player().uuid(),
                    request.player().name(),
                    node,
                    shortId,
                    codeHash(id, code),
                    expires,
                    now);
            if (created)
                return new SessionCreated(request.id(), v, id, shortId, code, publicUrl + "/" + shortId, expires);
        }
        return new ErrorMessage(request.id(), v, ErrorCode.INTERNAL, "No free short id");
    }

    /**
     * @param shortId the short id as typed
     * @return the pending session of that link while its code works
     */
    public Optional<StoredSession> pending(String shortId) {
        return repository.findPending(Secrets.normalise(shortId), clock.instant());
    }

    /**
     * Logs a browser in with a code.
     *
     * @param shortId the short id as typed
     * @param code the code as typed
     * @param device the browser, see {@link Devices}
     * @return the outcome
     */
    public LoginResult login(String shortId, String code, @Nullable String device) {
        Optional<StoredSession> found = pending(shortId);
        if (found.isEmpty()) return new LoginResult.Unknown();
        StoredSession session = found.get();
        if (session.state() == SessionState.OPENING) return new LoginResult.Busy();
        Optional<LiveServer> server = hub.server(session.serverId());
        Optional<NodeLink> link = server.flatMap(live -> live.routeTo(session.node()));
        if (link.isEmpty()) {
            return server.flatMap(Sessions::discontinued)
                    .<LoginResult>map(LoginResult.Discontinued::new)
                    .orElseGet(LoginResult.Offline::new);
        }
        String hash = session.codeHash();
        if (hash == null || !Secrets.matches(session.id() + ":" + Secrets.normalise(code), hash)) {
            return wrongCode(session);
        }
        if (!repository.startOpening(session.id())) return new LoginResult.Busy();
        NodeLink node = link.get();
        Message answer;
        try {
            answer = node.request(
                            id -> new SessionOpened(
                                    id, node.agreed().protocol(), session.id(), session.player(), device),
                            timeout)
                    .join();
        } catch (CompletionException e) {
            NodeRequestException failure = NodeRequestException.of(e);
            if (failure.code() == null) {
                repository.backToPending(session.id());
                return new LoginResult.Offline();
            }
            repository.delete(session.id());
            return new LoginResult.Refused(failure.getMessage() == null ? "Refused" : failure.getMessage());
        }
        if (!(answer instanceof SessionConfirmed)) {
            repository.delete(session.id());
            return new LoginResult.Refused("The server did not confirm the login");
        }
        String cookie = Secrets.token();
        if (!repository.bind(session.id(), Secrets.hash(cookie), device, clock.instant())) {
            return new LoginResult.Unknown();
        }
        return new LoginResult.LoggedIn(cookie, repository.find(session.id()).orElse(session));
    }

    private LoginResult wrongCode(StoredSession session) {
        int attempts = repository.countAttempt(session.id()).orElse(MAX_ATTEMPTS);
        if (attempts >= MAX_ATTEMPTS) {
            repository.delete(session.id()).ifPresent(gone -> end(gone, CloseReason.EXPIRED, true));
            return new LoginResult.WrongCode(0);
        }
        return new LoginResult.WrongCode(MAX_ATTEMPTS - attempts);
    }

    /**
     * Finds the session of a cookie and records the use.
     *
     * @param cookie the cookie value
     * @return the session while it lives
     */
    public Optional<StoredSession> authenticate(@Nullable String cookie) {
        if (cookie == null || cookie.isBlank()) return Optional.empty();
        Instant now = clock.instant();
        Optional<StoredSession> session =
                repository.findBound(Secrets.hash(cookie), now.minus(idle), now.minus(maxAge));
        session.ifPresent(found -> {
            if (found.lastAccess().isBefore(now.minus(TOUCH_INTERVAL))) repository.touch(found.id(), now);
        });
        return session;
    }

    /**
     * Records that a browser used a session, e.g. by pinging its stream.
     *
     * @param sessionId the session
     */
    public void touch(String sessionId) {
        repository.touch(sessionId, clock.instant());
    }

    /**
     * The browser logged out.
     *
     * @param session the session
     */
    public void logout(StoredSession session) {
        repository.delete(session.id()).ifPresent(gone -> end(gone, CloseReason.LOGOUT, true));
    }

    /**
     * The plugin answered a request with {@code unknown-session}: the session ended there.
     *
     * @param session the session
     */
    public void unknownToPlugin(StoredSession session) {
        repository.delete(session.id()).ifPresent(gone -> end(gone, CloseReason.UNKNOWN, false));
    }

    /**
     * The plugin ended a session.
     *
     * @param server the server
     * @param message the message
     */
    public void closedByPlugin(LiveServer server, SessionClosed message) {
        repository
                .find(message.session())
                .filter(session -> session.serverId().equals(server.id()))
                .flatMap(session -> repository.delete(session.id()))
                .ifPresent(gone -> end(gone, message.reason() == null ? CloseReason.UNKNOWN : message.reason(), false));
    }

    /**
     * The plugin ended every session of a player.
     *
     * @param server the server
     * @param message the message
     */
    public void revokedByPlugin(LiveServer server, SessionsRevoked message) {
        CloseReason reason = message.reason() == null ? CloseReason.REVOKED : message.reason();
        for (StoredSession gone : repository.deleteOfPlayer(server.id(), message.player())) end(gone, reason, false);
    }

    /**
     * Ends expired codes, idle and too old sessions, and the sessions of servers gone past the grace period. Called
     * every few seconds.
     */
    public void sweep() {
        Instant now = clock.instant();
        end(repository.deleteExpiredCodes(now), CloseReason.EXPIRED, true);
        end(repository.deleteIdle(now.minus(idle)), CloseReason.IDLE, true);
        end(repository.deleteOlderThan(now.minus(maxAge)), CloseReason.EXPIRED, true);
        end(repository.deleteOfServersGoneSince(now.minus(grace)), CloseReason.CONNECTION_LOST, false);
    }

    private void end(List<StoredSession> sessions, CloseReason reason, boolean tellPlugin) {
        for (StoredSession session : sessions) end(session, reason, tellPlugin);
    }

    private void end(StoredSession session, CloseReason reason, boolean tellPlugin) {
        snapshots.drop(session.id());
        reads.drop(session.id());
        streams.end(
                session.id(),
                new SessionClosed(null, Protocol.VERSION, session.id(), session.player(), reason),
                BrowserStreams.CLOSE_SESSION_ENDED);
        if (!tellPlugin) return;
        hub.server(session.serverId())
                .flatMap(server -> server.routeTo(session.node()))
                .ifPresent(link -> {
                    try {
                        link.send(new SessionClosed(
                                link.nextId(), link.agreed().protocol(), session.id(), session.player(), reason));
                    } catch (RuntimeException e) {
                        log.debug("Telling the plugin about an ended session failed", e);
                    }
                });
    }

    /**
     * @param server a server without a ready node
     * @return the policy's message when its nodes run a discontinued version
     */
    public static Optional<String> discontinued(LiveServer server) {
        return server.representative()
                .map(link -> link.agreed().status())
                .filter(status -> status.status() == VersionState.DISCONTINUED)
                .map(Sessions::message);
    }

    private static String message(VersionStatus status) {
        String message = status.message();
        return message == null ? "This version of Companies is no longer supported on the dashboard" : message;
    }

    private static String codeHash(String sessionId, String code) {
        return Secrets.hash(sessionId + ":" + code);
    }
}
