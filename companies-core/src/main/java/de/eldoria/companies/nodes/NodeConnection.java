/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.commands.suggest.SuggestionCache;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.nodes.NodeMessage.CacheInvalidate;
import de.eldoria.companies.nodes.NodeMessage.InboxNew;
import de.eldoria.companies.nodes.NodeMessage.SettingsChanged;
import de.eldoria.companies.services.notifications.InboxPoller;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.util.Times;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Connects the services of this node to the other nodes over the node bus.
 * <p>
 * Pending notifications and changed orders are announced, the order, price and suggestion caches of this node are
 * registered so other nodes can drop them, and the messages of the other nodes are acted on through
 * {@link NodeMessaging}. The primary announces its configuration once connected. On the {@link DatabaseBus} nothing is
 * sent or received and the polls and cache lifetimes stay in charge.
 */
public final class NodeConnection {
    private final NodeBus bus;
    private final NodeMessaging messaging;
    private final NodeSignals signals;

    private NodeConnection(NodeBus bus, NodeMessaging messaging) {
        this.bus = bus;
        this.messaging = messaging;
        this.signals = new NodeSignals(bus);
    }

    /**
     * Connects the services and starts acting on messages.
     *
     * @param parts the services of this node
     * @param timer runs the heartbeat
     * @return the connection; its {@link #signals()} still have to be registered as listener
     */
    public static NodeConnection connect(Parts parts, ScheduledExecutorService timer) {
        var bus = parts.bus();
        var caches = parts.caches();
        var orders = parts.orders();
        parts.notifications().announcePending((entry, recipient) -> bus.publish(new InboxNew(entry, recipient)));
        orders.onFullOrderChanged(id -> bus.publish(new CacheInvalidate(NodeCaches.ORDER, String.valueOf(id))));
        caches.register(NodeCaches.ORDER, key -> key.map(Integer::parseInt)
                .ifPresentOrElse(orders::forgetFullOrder, orders::forgetFullOrders));
        caches.register(NodeCaches.PRICES, key -> orders.invalidateMaterialPriceCache());
        caches.register(NodeCaches.COMPANY, key -> SuggestionCache.dropAll());
        caches.register(NodeCaches.ORDER, key -> SuggestionCache.dropAll());
        caches.register(NodeCaches.PLAYER, key -> SuggestionCache.dropAll());

        var inbox = parts.inbox();
        var messaging = new NodeMessaging(bus, caches, parts.presence(), parts.logger());
        messaging.start(
                new NodeMessaging.Node(
                        parts.primary(),
                        player -> inbox.deliver(player, Times.now()),
                        parts.settings()::changed,
                        () -> {
                            inbox.reschedule();
                            inbox.pollNow();
                        },
                        parts.onlineHere(),
                        parts.knownNodes()),
                timer);
        if (parts.primary()) {
            parts.revisions().get().forEach((path, revision) -> bus.publish(new SettingsChanged(path, revision)));
        } else {
            timer.scheduleWithFixedDelay(
                    parts.settings()::poll, SettingsSync.POLL_SECONDS, SettingsSync.POLL_SECONDS, TimeUnit.SECONDS);
        }
        return new NodeConnection(bus, messaging);
    }

    /**
     * @return turns the plugin's events on this node into messages; register it as listener
     */
    public NodeSignals signals() {
        return signals;
    }

    /**
     * Tells every node that the market prices were recalculated, so they drop their cached prices.
     */
    public void pricesRefreshed() {
        bus.publish(new CacheInvalidate(NodeCaches.PRICES, null));
    }

    /**
     * Tells the other nodes that this one stops, and closes the bus.
     */
    public void stop() {
        messaging.stop();
    }

    /**
     * The services of this node the connection needs.
     *
     * @param bus           the bus of this node
     * @param caches        the caches other nodes can invalidate
     * @param presence      where the players of the network are
     * @param logger        reports failures
     * @param primary       true on the primary node
     * @param notifications sends notifications
     * @param inbox         shows notifications sent from other nodes
     * @param orders        the order repository and its caches
     * @param settings      follows the shared files the primary stores, on a secondary
     * @param revisions     the latest revision of each shared file; the primary announces them when it connects
     * @param onlineHere    the players online on this node; blocks
     * @param knownNodes    the uids of the nodes registered in the database; blocks
     */
    public record Parts(
            NodeBus bus,
            NodeCaches caches,
            NetworkPresence presence,
            Logger logger,
            boolean primary,
            NotificationService notifications,
            InboxPoller inbox,
            AOrderData orders,
            SettingsSync settings,
            Supplier<Map<String, Integer>> revisions,
            Supplier<List<UUID>> onlineHere,
            Supplier<List<UUID>> knownNodes) {}
}
