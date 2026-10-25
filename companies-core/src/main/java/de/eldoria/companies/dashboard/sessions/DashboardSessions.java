/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.sessions;

import de.eldoria.companies.configuration.elements.DashboardSettings;
import de.eldoria.companies.data.repository.ADashboardData;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.web.api.session.CloseReason;
import de.eldoria.companies.web.api.session.PlayerRef;
import de.eldoria.companies.web.api.session.SessionCreated;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The dashboard sessions the nodes of this network created, kept in the database.
 * <p>
 * A gateway can only invent sessions the plugin never created; this registry is how the plugin tells. A session is
 * pending until a browser logs in with its code, then open until the player logs out, an admin revokes it, it stays
 * unused longer than {@code dashboard.sessionIdleMinutes} or it gets older than {@code dashboard.sessionMaxHours}. A
 * pending session ends when its code expires. Any node may end a session; deleting the row decides which node reports
 * it. The methods block on the database.
 */
public final class DashboardSessions {
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final ADashboardData data;
    private final Supplier<DashboardSettings> settings;
    private final UUID node;
    private final Clock clock;
    private final Map<String, LocalDateTime> touched = new ConcurrentHashMap<>();

    /**
     * @param data     the dashboard tables
     * @param settings the current dashboard settings
     * @param node     the uid of this node
     * @param clock    the current time
     */
    public DashboardSessions(ADashboardData data, Supplier<DashboardSettings> settings, UUID node, Clock clock) {
        this.data = data;
        this.settings = settings;
        this.node = node;
        this.clock = clock;
    }

    /**
     * Stores a session the gateway created for a player of this node.
     *
     * @param created     the gateway's answer
     * @param player      the player
     * @param permissions the player's permissions that matter to the dashboard
     * @return the stored session
     */
    public StoredSession created(SessionCreated created, PlayerRef player, Set<String> permissions) {
        var now = now();
        var expires = LocalDateTime.ofInstant(created.expiresAt(), ZoneOffset.UTC);
        var session = new StoredSession(
                created.session(),
                player.uuid(),
                player.name(),
                node,
                now,
                expires,
                now,
                null,
                null,
                Set.copyOf(permissions));
        data.saveSession(session);
        return session;
    }

    /**
     * Marks a pending session open after a browser entered its code.
     *
     * @param id     the session id
     * @param player the player the gateway named
     * @param device the browser the gateway named
     * @return the opened session; empty when no pending session of that player has the id or its code expired
     */
    public Optional<StoredSession> open(String id, UUID player, @Nullable String device) {
        var session = data.session(id).filter(found -> found.player().equals(player));
        if (session.isEmpty() || session.get().isOpen()) return Optional.empty();
        var now = now();
        if (session.get().codeExpires().isBefore(now)) return Optional.empty();
        if (!data.open(id, now, device)) return Optional.empty();
        return data.session(id);
    }

    /**
     * Finds an open session of a player that has not ended, and records that it was used.
     *
     * @param id     the session id
     * @param player the player the gateway named
     * @return the session, empty when the plugin does not know it as an open session of that player
     */
    public Optional<StoredSession> active(String id, UUID player) {
        var session = data.session(id)
                .filter(found -> found.player().equals(player))
                .filter(StoredSession::isOpen)
                .filter(found -> endReason(found, now()).isEmpty());
        session.ifPresent(this::touch);
        return session;
    }

    /**
     * Counts the sessions a browser watches as used, so one that only watches the live updates is not ended as idle.
     * A browser stops watching when the gateway drops its snapshot, which happens a while after the browser went quiet.
     *
     * @param sessions the ids of the watched sessions
     */
    public void watched(Set<String> sessions) {
        for (var id : sessions) {
            data.session(id).filter(StoredSession::isOpen).ifPresent(this::touch);
        }
    }

    private void touch(StoredSession session) {
        var now = now();
        var last = touched.get(session.id());
        if (last != null && last.plus(TOUCH_INTERVAL).isAfter(now)) return;
        touched.put(session.id(), now);
        data.touch(session.id(), now);
    }

    /**
     * Whether a session still exists, pending or open.
     *
     * @param id the session id
     * @return true while it exists
     */
    public boolean exists(String id) {
        return data.session(id).isPresent();
    }

    /**
     * The sessions of a player.
     *
     * @param player the player
     * @return the sessions, oldest first
     */
    public List<StoredSession> of(UUID player) {
        return data.sessionsOf(player);
    }

    /**
     * Ends every session of a player.
     *
     * @param player the player
     * @return the sessions this call ended
     */
    public List<StoredSession> endAll(UUID player) {
        List<StoredSession> ended = new ArrayList<>();
        for (var session : data.sessionsOf(player)) {
            if (forget(session.id())) ended.add(session);
        }
        return ended;
    }

    /**
     * Ends one session.
     *
     * @param id the session id
     * @return true when this call ended it
     */
    public boolean forget(String id) {
        touched.remove(id);
        return data.removeSession(id);
    }

    /**
     * Ends the sessions that were idle too long, got too old or whose code expired.
     *
     * @return the sessions this call ended with the reason
     */
    public List<Ended> sweep() {
        var now = now();
        var current = settings.get();
        List<Ended> ended = new ArrayList<>();
        for (var session : data.ended(
                now.minusMinutes(current.sessionIdleMinutes()), now.minusHours(current.sessionMaxHours()), now)) {
            var reason = endReason(session, now).orElse(CloseReason.EXPIRED);
            if (forget(session.id())) ended.add(new Ended(session, reason));
        }
        return ended;
    }

    private Optional<CloseReason> endReason(StoredSession session, LocalDateTime now) {
        var current = settings.get();
        if (!session.isOpen() && session.codeExpires().isBefore(now)) return Optional.of(CloseReason.EXPIRED);
        if (session.created().plusHours(current.sessionMaxHours()).isBefore(now)) {
            return Optional.of(CloseReason.EXPIRED);
        }
        if (session.lastActive().plusMinutes(current.sessionIdleMinutes()).isBefore(now)) {
            return Optional.of(CloseReason.IDLE);
        }
        return Optional.empty();
    }

    /**
     * Replaces the remembered permissions of a player's sessions.
     *
     * @param player      the player
     * @param permissions the permissions seen now
     */
    public void permissions(UUID player, Set<String> permissions) {
        data.permissions(player, permissions);
    }

    /**
     * @return every session of the network, waiting ones included
     */
    public List<StoredSession> all() {
        return players().stream().flatMap(player -> of(player).stream()).toList();
    }

    /**
     * @return the players with a session
     */
    public Set<UUID> players() {
        return data.playersWithSessions();
    }

    /**
     * @return the number of sessions of the network, pending ones included
     */
    public int count() {
        return data.sessionCount();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * A session that ended.
     *
     * @param session the session
     * @param reason  why it ended
     */
    public record Ended(StoredSession session, CloseReason reason) {}
}
