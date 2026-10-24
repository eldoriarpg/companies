/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import de.eldoria.companies.dashboard.sessions.DashboardSessions;
import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.session.PlayerPresence;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tells the gateway on which node the players with a dashboard session are online, so it routes the actions that move
 * items to where the player's inventory is.
 * <p>
 * Every report goes through one ordered queue: a join and a quit of the same player, or a quit and the report of every
 * online player after a welcome, reach the gateway in the order they happened here, whatever the database lookups in
 * between take. Players without a session are never reported. A quit is reported for every player with a session,
 * which the gateway learned of when the session was created in game, and for every player this node announced, also
 * when the player's session ended meanwhile, so the gateway never keeps a player it was told about. The players
 * online on this node are kept here from the joins and quits.
 */
public final class PresenceReporter {
    private final Consumer<Message> send;
    private final DashboardSessions sessions;
    private final UUID node;
    private final Executor work;
    private final Logger logger;
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private final Set<UUID> announced = ConcurrentHashMap.newKeySet();
    private CompletableFuture<Void> queue = CompletableFuture.completedFuture(null);

    /**
     * @param send     sends to the gateway
     * @param sessions the plugin's sessions
     * @param node     this node's uid
     * @param work     runs the reports off the main thread
     * @param logger   logger of the plugin
     */
    public PresenceReporter(
            Consumer<Message> send, DashboardSessions sessions, UUID node, Executor work, Logger logger) {
        this.send = send;
        this.sessions = sessions;
        this.node = node;
        this.work = work;
        this.logger = logger;
    }

    /**
     * Takes the players online when the dashboard starts.
     *
     * @param players the players online on this node
     */
    public void online(Collection<UUID> players) {
        online.addAll(players);
    }

    /**
     * A player joined this node.
     *
     * @param player the player
     */
    public void joined(UUID player) {
        online.add(player);
        enqueue(() -> announce(player));
    }

    /**
     * A player left this node.
     *
     * @param player the player
     */
    public void quit(UUID player) {
        online.remove(player);
        enqueue(() -> {
            if (announced.remove(player) || !sessions.of(player).isEmpty()) send(player, false);
        });
    }

    /**
     * Reports every player with a session online on this node, after the gateway welcomed this node and whenever the
     * permissions are refreshed, so a gateway that lost track learns it again.
     */
    public void reportAll() {
        enqueue(() -> {
            var withSessions = sessions.players();
            for (var player : Set.copyOf(online)) {
                if (withSessions.contains(player)) {
                    announced.add(player);
                    send(player, true);
                }
            }
        });
    }

    private void announce(UUID player) {
        if (!online.contains(player) || sessions.of(player).isEmpty()) return;
        announced.add(player);
        send(player, true);
    }

    private void send(UUID player, boolean isOnline) {
        send.accept(new PlayerPresence(null, Protocol.VERSION, player, isOnline, node));
    }

    private synchronized void enqueue(Runnable report) {
        queue = queue.thenRunAsync(report, work).exceptionally(err -> {
            logger.log(Level.FINE, "Could not report a player's presence to the gateway.", err);
            return null;
        });
    }
}
