/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import de.eldoria.companies.configuration.elements.DashboardSettings;
import de.eldoria.companies.dashboard.feed.PatchFeed;
import de.eldoria.companies.dashboard.gateway.Backoff;
import de.eldoria.companies.dashboard.gateway.GatewayConnection;
import de.eldoria.companies.dashboard.gateway.GatewayLink;
import de.eldoria.companies.dashboard.gateway.GatewayStatus;
import de.eldoria.companies.dashboard.gateway.Handshake;
import de.eldoria.companies.dashboard.keys.DashboardKeys;
import de.eldoria.companies.dashboard.sessions.DashboardSessions;
import de.eldoria.companies.data.wrapper.dashboard.StoredKey;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.handshake.GatewayKind;
import de.eldoria.companies.web.api.handshake.Hello;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.handshake.VersionState;
import de.eldoria.companies.web.api.handshake.Welcome;
import de.eldoria.companies.web.api.link.ErrorMessage;
import de.eldoria.companies.web.api.session.CloseReason;
import de.eldoria.companies.web.api.session.CreateSession;
import de.eldoria.companies.web.api.session.PlayerRef;
import de.eldoria.companies.web.api.session.SessionClosed;
import de.eldoria.companies.web.api.session.SessionCreated;
import de.eldoria.companies.web.api.session.SessionsRevoked;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The web dashboard of this node: its connection to the gateway, the sessions, snapshots and patches.
 * <p>
 * Every node connects on its own, since events stay on the node where they happen. The connection is replaced on
 * {@code /companyadmin reload}, which reads the gateway, the branding and whether the dashboard is enabled again. The
 * sessions, the subscriptions and what the gateway said live as long as the plugin. Every minute ended sessions are
 * closed; on the primary the dashboard key is refreshed once a day.
 */
public final class Dashboard implements GatewayLink {
    private static final Duration CREATE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);
    private static final Duration KEY_CHECK_INTERVAL = Duration.ofMinutes(1);
    private static final Duration RANKING_INTERVAL = Duration.ofMinutes(1);
    private static final Duration MARKET_HISTORY_INTERVAL = Duration.ofMinutes(30);
    private static final URI HOSTED = URI.create(DashboardSettings.DEFAULT_GATEWAY);

    private final Parts parts;
    private final List<ScheduledFuture<?>> tasks = new ArrayList<>();
    private volatile @Nullable GatewayConnection connection;
    private volatile @Nullable URI gateway;
    private boolean stopped;
    private final Object outage = new Object();
    private @Nullable Instant offlineSince;
    private @Nullable Instant excusing;

    /**
     * @param parts what the dashboard is made of
     */
    public Dashboard(Parts parts) {
        this.parts = parts;
        this.offlineSince = parts.clock().instant();
        parts.sessions().holdIdle(true);
    }

    /**
     * The gateway welcomed this node. On the primary, the time the dashboard could not be reached is excused from the
     * sessions' idle time, as the gateway excuses its own downtime; only the primary does it, so it happens once. Idle
     * sessions end again only once that is done. A disconnect while the excuse runs starts a new outage, which the next
     * welcome excuses in turn. The outage's start, the excuse under way and the hold on idle sessions change together
     * under one lock.
     */
    public void welcomed() {
        Instant since;
        Duration downtime;
        synchronized (outage) {
            since = offlineSince;
            if (since == null || excusing != null) return;
            downtime = Duration.between(since, parts.clock().instant());
            if (!parts.primary() || !downtime.isPositive()) {
                endOutage(since);
                return;
            }
            excusing = since;
        }
        parts.work().execute(() -> {
            try {
                parts.sessions().excuseIdle(downtime);
            } catch (RuntimeException e) {
                parts.logger().log(Level.WARNING, "Could not excuse the dashboard's downtime from the sessions.", e);
            } finally {
                synchronized (outage) {
                    excusing = null;
                    endOutage(since);
                }
            }
        });
    }

    private boolean reachable() {
        synchronized (outage) {
            return offlineSince == null;
        }
    }

    private void endOutage(Instant since) {
        if (!since.equals(offlineSince)) return;
        offlineSince = null;
        parts.sessions().holdIdle(false);
    }

    /**
     * Starts the scheduled work and, when the dashboard is enabled, the connection.
     *
     * @param timer runs the scheduled work
     */
    public synchronized void start(ScheduledExecutorService timer) {
        tasks.add(timer.scheduleAtFixedRate(
                this::sweep, SWEEP_INTERVAL.toSeconds(), SWEEP_INTERVAL.toSeconds(), TimeUnit.SECONDS));
        tasks.add(timer.scheduleAtFixedRate(
                parts.feed()::pushRankings,
                RANKING_INTERVAL.toSeconds(),
                RANKING_INTERVAL.toSeconds(),
                TimeUnit.SECONDS));
        if (parts.primary()) {
            tasks.add(timer.scheduleAtFixedRate(
                    this::checkKey, KEY_CHECK_INTERVAL.toSeconds(), KEY_CHECK_INTERVAL.toSeconds(), TimeUnit.SECONDS));
            tasks.add(timer.scheduleAtFixedRate(
                    () -> parts.work().execute(parts.marketHistory()),
                    SWEEP_INTERVAL.toSeconds(),
                    MARKET_HISTORY_INTERVAL.toSeconds(),
                    TimeUnit.SECONDS));
        }
        restartLater();
    }

    /**
     * Replaces the connection off the calling thread, so a reload on the main thread waits neither for the old
     * connection to close nor for the database.
     */
    public void restartLater() {
        parts.work().execute(() -> {
            try {
                restart();
            } catch (RuntimeException e) {
                parts.logger().log(Level.WARNING, "Could not restart the dashboard connection.", e);
            }
        });
    }

    /**
     * Replaces the connection with one built from the current settings, or stops it while the dashboard is disabled.
     * The old connection is closed before the new one opens, so the gateway never sees both. On the primary the key
     * configured in {@code node.yml} is stored when it changed. Blocks while the old connection closes. Does nothing
     * once the dashboard was stopped, so a restart still queued then opens no connection.
     */
    public synchronized void restart() {
        if (stopped) return;
        var previous = connection;
        connection = null;
        if (previous != null) previous.stop();
        var settings = parts.settings().get();
        if (!settings.enabled()) {
            gateway = null;
            return;
        }
        var uri = settings.gateway();
        if (uri.isEmpty()) {
            parts.logger().warning("dashboard.gateway is no ws:// or wss:// address. The dashboard stays off.");
            return;
        }
        gateway = uri.get();
        // TODO: with a realtime bus (Redis) only the primary connects and relays the other nodes' patches and actions
        parts.status().allowLynaKey();
        if (parts.primary()) seedKey();
        var address = uri.get();
        var next = new GatewayConnection(
                new GatewayConnection.Endpoint(address, parts.userAgent()),
                GatewayConnection.Timing.standard(new Backoff(Math::random)),
                () -> hello(address),
                parts.inbound(),
                parts.status()::discontinued,
                this::disconnected,
                parts.clock(),
                parts.logger());
        connection = next;
        next.start();
    }

    private void seedKey() {
        try {
            parts.keys().seed(configuredKey());
        } catch (RuntimeException e) {
            parts.logger().log(Level.WARNING, "Could not store the dashboard key from node.yml.", e);
        }
    }

    private Optional<String> configuredKey() {
        return Optional.of(parts.configuredKey().get().trim());
    }

    /**
     * The hello of a new connection. A Lyna dashboard key is sent only over {@code wss://} to the gateway hosted by
     * Eldoria, and not again to a gateway that called itself self-hosted; without a hello the connection waits.
     *
     * @param address the gateway's address
     * @return the hello, empty when there is no key or the key must not go to this gateway
     */
    private Optional<Hello> hello(URI address) {
        var hello = parts.handshake().hello();
        if (hello.isEmpty() || hello.get().credentials().kind() != KeyKind.DASHBOARD_KEY) return hello;
        if (parts.status().lynaKeyWithheld()) return Optional.empty();
        if (!hosted(address)) {
            parts.status()
                    .withholdLynaKey(
                            "a Lyna dashboard key is only sent to " + HOSTED.getHost()
                                    + " over wss://; a self-hosted gateway needs its own server token",
                            false);
            return Optional.empty();
        }
        return hello;
    }

    /**
     * Whether an address is the gateway hosted by Eldoria, reached over TLS.
     *
     * @param address the gateway's address
     * @return true for {@code wss://} to the host of {@link DashboardSettings#DEFAULT_GATEWAY}
     */
    public static boolean hosted(URI address) {
        return "wss".equalsIgnoreCase(address.getScheme()) && HOSTED.getHost().equalsIgnoreCase(address.getHost());
    }

    /**
     * Closes the current connection without sending the key again, after a gateway that called itself self-hosted
     * received a Lyna dashboard key. A new key or a reload connects again.
     */
    public void disconnect() {
        var current = connection;
        if (current != null) current.reconnect();
    }

    /**
     * Closes the connection and stops the scheduled work.
     */
    public synchronized void stop() {
        stopped = true;
        tasks.forEach(task -> task.cancel(false));
        tasks.clear();
        var current = connection;
        connection = null;
        if (current != null) current.stop();
    }

    private void disconnected(GatewayConnection.Closed closed) {
        synchronized (outage) {
            if (offlineSince == null || offlineSince.equals(excusing))
                offlineSince = parts.clock().instant();
            parts.sessions().holdIdle(true);
        }
        parts.status().disconnected(closed);
        parts.feed().clear();
    }

    @Override
    public boolean send(Message message) {
        var current = connection;
        return current != null && current.send(message);
    }

    @Override
    public CompletableFuture<Message> request(Message request, Duration timeout) {
        var current = connection;
        if (current == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("The dashboard is not connected."));
        }
        return current.request(request, timeout);
    }

    /**
     * Asks the gateway for a session of a player and stores it.
     *
     * @param player      the player
     * @param permissions the player's permissions that matter to the dashboard
     * @return the created session, or why none was created
     */
    public CompletableFuture<Opening> open(PlayerRef player, Set<String> permissions) {
        if (!parts.settings().get().enabled()) return done(new Opening.Refused("dashboard.disabled", null));
        var welcome = parts.status().lastWelcome();
        if (welcome.isPresent() && welcome.get().version().status() == VersionState.DISCONTINUED) {
            return done(new Opening.Refused(
                    "dashboard.discontinued", GatewayStatus.notice(welcome.get().version())));
        }
        if (!parts.status().connected()) return done(new Opening.Refused("dashboard.notConnected", null));
        var request = new CreateSession(UUID.randomUUID().toString(), Protocol.VERSION, player, parts.node());
        return request(request, CREATE_TIMEOUT)
                .thenApplyAsync(answer -> created(answer, player, permissions), parts.work())
                .exceptionally(error -> new Opening.Refused("dashboard.notConnected", null));
    }

    private Opening created(Message answer, PlayerRef player, Set<String> permissions) {
        if (answer instanceof SessionCreated created) {
            parts.sessions().created(created, player, permissions);
            return new Opening.Created(created);
        }
        if (answer instanceof ErrorMessage error) {
            parts.logger().fine("The gateway refused a dashboard session: " + error.code());
        }
        return new Opening.Refused("dashboard.refused", null);
    }

    private static CompletableFuture<Opening> done(Opening opening) {
        return CompletableFuture.completedFuture(opening);
    }

    /**
     * The sessions of a player.
     *
     * @param player the player
     * @return the sessions
     */
    public CompletableFuture<List<StoredSession>> sessions(UUID player) {
        return CompletableFuture.supplyAsync(() -> parts.sessions().of(player), parts.work());
    }

    /**
     * Ends every session of a player and tells the gateway.
     *
     * @param player the player
     * @param reason why, {@link CloseReason#LOGOUT} or {@link CloseReason#REVOKED}
     * @return the number of sessions ended
     */
    public CompletableFuture<Integer> endSessions(UUID player, CloseReason reason) {
        return CompletableFuture.supplyAsync(
                () -> {
                    var ended = parts.sessions().endAll(player);
                    ended.forEach(session -> parts.feed().unsubscribe(session.id()));
                    send(new SessionsRevoked(null, Protocol.VERSION, player, reason));
                    return ended.size();
                },
                parts.work());
    }

    /**
     * Stores a key entered by an admin and connects with it.
     *
     * @param secret the key or token
     * @return the stored key
     */
    public CompletableFuture<StoredKey> enterKey(String secret) {
        return CompletableFuture.supplyAsync(
                () -> {
                    var key = parts.keys().enter(secret, configuredKey());
                    parts.status().allowLynaKey();
                    var current = connection;
                    if (current != null) current.reconnect();
                    parts.keyChanged().run();
                    return key;
                },
                parts.work());
    }

    /**
     * Connects again after another node stored a new key.
     */
    public void keyChangedElsewhere() {
        parts.status().allowLynaKey();
        var current = connection;
        if (current != null) current.reconnect();
    }

    /**
     * Remembers the permissions a player has now with the player's sessions, and ends the sessions of a player who may
     * no longer use the dashboard.
     *
     * @param player      the player
     * @param permissions the player's permissions that matter to the dashboard
     * @param mayUse      whether the player may use the dashboard
     */
    public void permissionsSeen(UUID player, Set<String> permissions, boolean mayUse) {
        parts.work().execute(() -> {
            try {
                if (parts.sessions().of(player).isEmpty()) return;
                if (!mayUse) {
                    endSessions(player, CloseReason.REVOKED);
                    return;
                }
                parts.sessions().permissions(player, permissions);
            } catch (RuntimeException e) {
                parts.logger().log(Level.FINE, "Could not refresh the dashboard permissions of a player.", e);
            }
        });
    }

    /**
     * The players with a session, to refresh the permissions of those online.
     *
     * @return the players
     */
    public CompletableFuture<Set<UUID>> playersWithSessions() {
        return CompletableFuture.supplyAsync(() -> parts.sessions().players(), parts.work());
    }

    private void sweep() {
        try {
            for (var gone : parts.sessions().watched(parts.feed().subscribed()))
                parts.feed().unsubscribe(gone);
            for (var ended : parts.sessions().sweep(parts.primary() && reachable())) {
                parts.feed().unsubscribe(ended.session().id());
                send(new SessionClosed(
                        null,
                        Protocol.VERSION,
                        ended.session().id(),
                        ended.session().player(),
                        ended.reason()));
            }
        } catch (RuntimeException e) {
            parts.logger().log(Level.WARNING, "Could not end the expired dashboard sessions.", e);
        }
    }

    private void checkKey() {
        if (!parts.settings().get().enabled()) return;
        if (parts.status().lastWelcome().map(Welcome::gateway).orElse(GatewayKind.HOSTED) == GatewayKind.SELF_HOSTED) {
            return;
        }
        try {
            parts.keys().refreshIfDue();
        } catch (RuntimeException e) {
            parts.logger().log(Level.WARNING, "Could not refresh the dashboard key.", e);
        }
    }

    /**
     * What the dashboard of this node does now. Never contains the key.
     *
     * @return the status
     */
    public CompletableFuture<Status> status() {
        return CompletableFuture.supplyAsync(
                () -> {
                    var current = connection;
                    var key = parts.keys().current();
                    return new Status(
                            parts.settings().get().enabled(),
                            current == null ? GatewayConnection.State.STOPPED : current.state(),
                            Optional.ofNullable(gateway),
                            parts.status().lastWelcome(),
                            key.map(StoredKey::kind),
                            key.flatMap(StoredKey::refresh).map(Object::toString),
                            parts.keys().problem().or(() -> parts.status().refusal()),
                            parts.status().notices(),
                            parts.sessions().count(),
                            parts.feed().subscriptions());
                },
                parts.work());
    }

    /**
     * @return the notices admins see on join, empty when there are none
     */
    public List<String> notices() {
        return parts.status().notices();
    }

    /**
     * The outcome of {@link #open}.
     */
    public sealed interface Opening {
        /**
         * The gateway created the session.
         *
         * @param session the gateway's answer with link and code
         */
        record Created(SessionCreated session) implements Opening {}

        /**
         * No session was created.
         *
         * @param reason  the message key of the reason
         * @param message a sentence from the gateway, e.g. why the version is discontinued
         */
        record Refused(String reason, @Nullable String message) implements Opening {}
    }

    /**
     * What the dashboard of this node does.
     *
     * @param enabled         whether the dashboard is enabled
     * @param state           what the connection does
     * @param gateway         the gateway's address
     * @param welcome         what the gateway said on the last connection
     * @param keyKind         the kind of the stored key
     * @param refreshAt       when Lyna hands out the next key, UTC
     * @param problem         why the key was refused
     * @param notices         deprecation notices for admins
     * @param sessions        the sessions of the network
     * @param subscriptions   the sessions this node pushes patches to
     */
    public record Status(
            boolean enabled,
            GatewayConnection.State state,
            Optional<URI> gateway,
            Optional<Welcome> welcome,
            Optional<KeyKind> keyKind,
            Optional<String> refreshAt,
            Optional<String> problem,
            List<String> notices,
            int sessions,
            int subscriptions) {}

    /**
     * What the dashboard is made of.
     *
     * @param settings      the current dashboard settings
     * @param configuredKey the key in {@code node.yml} as written
     * @param keys          the network's key
     * @param sessions      the network's sessions
     * @param status        what the gateway said
     * @param handshake     builds the hello
     * @param feed          the subscriptions and patches
     * @param marketHistory records the market's price history, on the primary node
     * @param inbound       answers the gateway's messages
     * @param keyChanged    tells the other nodes that a new key was stored
     * @param node          this node's uid
     * @param primary       whether this node is the primary
     * @param userAgent     {@code Companies/<version>}
     * @param work          runs database work off the main thread
     * @param clock         the current time
     * @param logger        logger of the plugin
     */
    public record Parts(
            Supplier<DashboardSettings> settings,
            Supplier<String> configuredKey,
            DashboardKeys keys,
            DashboardSessions sessions,
            GatewayStatus status,
            Handshake handshake,
            PatchFeed feed,
            Runnable marketHistory,
            Consumer<Message> inbound,
            Runnable keyChanged,
            UUID node,
            boolean primary,
            String userAgent,
            Executor work,
            InstantSource clock,
            Logger logger) {}

    /**
     * Turns a key kind into the word shown to admins.
     *
     * @param kind the kind
     * @return {@code key} or {@code server token}
     */
    public static String describe(KeyKind kind) {
        return kind == KeyKind.DASHBOARD_KEY ? "Lyna dashboard key" : "server token";
    }
}
