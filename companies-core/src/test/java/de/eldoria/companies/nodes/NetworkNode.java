/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.TestMappers;
import de.eldoria.companies.data.repository.AInboxData;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.repository.ANodeMessageData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbInboxData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNodeData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNodeMessageData;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbOrderData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresInboxData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNodeData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNodeMessageData;
import de.eldoria.companies.data.repository.impl.postgres.PostgresOrderData;
import de.eldoria.companies.data.wrapper.nodes.Node;
import de.eldoria.companies.services.notifications.InboxPoller;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.services.notifications.OnlinePlayers;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * One node of a test network: its own files, node settings, repositories on the shared database, notification
 * service, inbox poller and bus, connected through {@link NodeConnection} as the plugin does it. All nodes run in one
 * JVM on one MockBukkit server; each node has its own set of players that count as online on it.
 */
final class NetworkNode implements AutoCloseable {
    static final RedisBus.Timings FAST =
            new RedisBus.Timings(Duration.ofMillis(300), Duration.ofMillis(1500), Duration.ofMillis(500));

    final String name;
    final UUID uid;
    final boolean primary;
    final Configuration configuration;
    final ANodeData nodeData;
    final AInboxData inbox;
    final AOrderData orders;
    final ANodeMessageData messages;
    final NotificationService notifications;
    final InboxPoller poller;
    final NodeBus bus;
    final NodeCaches caches;
    final NetworkPresence presence;
    final NodeConnection connection;
    final Set<UUID> online = ConcurrentHashMap.newKeySet();
    final List<Shown> shown = new CopyOnWriteArrayList<>();
    final AtomicInteger reloads = new AtomicInteger();
    final AtomicInteger cacheDrops = new AtomicInteger();
    final List<String> probes = new CopyOnWriteArrayList<>();

    private final ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(2);
    private final ExecutorService handlers = Executors.newVirtualThreadPerTaskExecutor();
    private final Plugin plugin;

    /**
     * A notification shown on this node.
     *
     * @param player recipient
     * @param type   kind of notification
     */
    record Shown(UUID player, NotificationType type) {}

    private NetworkNode(Setup setup) throws IOException {
        name = setup.name();
        uid = setup.uid();
        primary = setup.primary();
        var logger = Logger.getLogger("node-" + name);
        Files.createDirectories(setup.folder());
        Files.writeString(
                setup.folder().resolve("node.yml"),
                "nodeType: " + (primary ? "PRIMARY" : "SECONDARY") + "\nnodeUid: " + uid + "\n");
        Files.writeString(
                setup.folder().resolve("database.yml"),
                "storageType: " + setup.dialect().name() + "\n");
        configuration = new Configuration(configPlugin(setup.folder(), logger));
        nodeData = setup.dialect().nodeData(configuration);
        configuration.syncConfigurations(nodeData).join();

        inbox = setup.dialect().inbox(handlers);
        orders = setup.dialect().orders(handlers);
        messages = setup.dialect().messages();
        plugin = MockBukkit.createMockPlugin(
                "Node" + name + UUID.randomUUID().toString().substring(0, 8));
        StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        var settings = settings(setup.pollSeconds());
        notifications = new NotificationService(
                plugin,
                inbox,
                orders,
                pool,
                () -> settings,
                (player, type, args) -> shown.add(new Shown(player.getUniqueId(), type)),
                Runnable::run);
        notifications.useOnlinePlayers(players(setup.server()));
        poller = new InboxPoller(inbox, notifications, logger);

        bus = setup.bus().create(this, handlers, logger);
        caches = new NodeCaches(logger);
        caches.register("probe", key -> key.ifPresentOrElse(probes::add, cacheDrops::incrementAndGet));
        presence = new NetworkPresence(setup.clock());
        poller.start(pool, () -> settings, bus::realtime);
        connection = NodeConnection.connect(
                new NodeConnection.Parts(
                        bus,
                        caches,
                        presence,
                        logger,
                        primary,
                        notifications,
                        poller,
                        orders,
                        NodeConnection.configSync(
                                configuration, nodeData, Runnable::run, reloads::incrementAndGet, logger),
                        () -> List.copyOf(online),
                        () -> nodeData.nodes().stream().map(Node::uid).toList()),
                pool);
    }

    /**
     * Starts a node and registers it in the database, the primary writing its configuration and a secondary loading
     * it.
     *
     * @param setup how the node is set up
     * @return the running node
     */
    static NetworkNode start(Setup setup) {
        try {
            return new NetworkNode(setup);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param player player to show as online on this node
     */
    void join(Player player) {
        online.add(player.getUniqueId());
    }

    /**
     * @param player player to show as offline on this node
     */
    void quit(Player player) {
        online.remove(player.getUniqueId());
    }

    /**
     * @param player a player
     * @return how often a notification was shown to the player on this node
     */
    long shownTo(Player player) {
        return shown.stream()
                .filter(entry -> entry.player().equals(player.getUniqueId()))
                .count();
    }

    /**
     * Stops the node without telling the others, as a crashed server would.
     */
    void crash() {
        pool.shutdownNow();
        bus.close();
    }

    @Override
    public void close() {
        connection.stop();
        pool.shutdownNow();
        handlers.shutdownNow();
        MessageSender.unregister(plugin);
    }

    private OnlinePlayers players(ServerMock server) {
        return new OnlinePlayers() {
            @Override
            public @Nullable Player find(UUID player) {
                return online.contains(player) ? server.getPlayer(player) : null;
            }

            @Override
            public List<UUID> ids() {
                return List.copyOf(online);
            }
        };
    }

    private static NotificationSettings settings(int pollSeconds) {
        return YAMLMapper.builder()
                .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                .build()
                .readValue("pollSeconds: " + pollSeconds, NotificationSettings.class);
    }

    private static Plugin configPlugin(Path folder, Logger logger) {
        var meta = (PluginMeta) Proxy.newProxyInstance(
                NetworkNode.class.getClassLoader(),
                new Class<?>[] {PluginMeta.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getVersion" -> "1.0.0";
                    case "getName" -> "Companies";
                    default -> null;
                });
        return (Plugin) Proxy.newProxyInstance(
                NetworkNode.class.getClassLoader(),
                new Class<?>[] {Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDataFolder" -> folder.toFile();
                    case "getLogger" -> logger;
                    case "getName" -> "Companies";
                    case "getPluginMeta" -> meta;
                    default -> null;
                });
    }

    /**
     * Sends a heartbeat at once, as the node does every 30 seconds.
     */
    void beat() {
        bus.publish(
                new NodeMessage.Heartbeat(List.copyOf(online), bus.transport().id()));
    }

    /**
     * How a node is set up.
     *
     * @param name        short name for logs
     * @param server      the MockBukkit server all nodes share
     * @param folder      folder of the node's files
     * @param uid         node uid
     * @param primary     true for the primary node
     * @param dialect     the database all nodes share
     * @param bus         creates the bus of the node
     * @param pollSeconds inbox poll interval while the bus is not realtime
     * @param clock       clock of the presence
     */
    record Setup(
            String name,
            ServerMock server,
            Path folder,
            UUID uid,
            boolean primary,
            Dialect dialect,
            Buses bus,
            int pollSeconds,
            Clock clock) {}

    /**
     * Creates the bus of a node.
     */
    @FunctionalInterface
    interface Buses {
        /**
         * @param node     the node, with its uid, role and message table set
         * @param handlers runs the handlers of received messages
         * @param logger   logger of the node
         * @return the started bus
         */
        NodeBus create(NetworkNode node, ExecutorService handlers, Logger logger);

        /**
         * @param server the Redis server and channel prefix
         * @return Redis only
         */
        static Buses redis(RedisBus.Server server) {
            return (node, handlers, logger) ->
                    RedisBus.start(server, node.uid, handlers, logger, Duration.ofSeconds(5), FAST);
        }

        /**
         * @param timings how the message table is read and cleaned up
         * @return low latency mode only
         */
        static Buses lowLatency(MessageTableBus.Timings timings) {
            return (node, handlers, logger) ->
                    MessageTableBus.start(node.messages, node.uid, node.primary, handlers, logger, timings);
        }

        /**
         * @param server  the Redis server and channel prefix
         * @param timings how the message table is read and cleaned up
         * @return Redis with low latency mode as fallback
         */
        static Buses failover(RedisBus.Server server, MessageTableBus.Timings timings) {
            return (node, handlers, logger) -> FailoverBus.start(
                    RedisBus.start(server, node.uid, handlers, logger, Duration.ofSeconds(5), FAST),
                    MessageTableBus.create(node.messages, node.uid, node.primary, handlers, logger, timings));
        }
    }

    /**
     * The repositories of one database type.
     */
    enum Dialect {
        POSTGRES {
            @Override
            ANodeData nodeData(Configuration configuration) {
                return new PostgresNodeData(configuration.nodeSettings());
            }

            @Override
            AInboxData inbox(ExecutorService executor) {
                return new PostgresInboxData(executor);
            }

            @Override
            AOrderData orders(ExecutorService executor) {
                return new PostgresOrderData(executor, TestMappers.items());
            }

            @Override
            ANodeMessageData messages() {
                return new PostgresNodeMessageData();
            }
        },
        MARIADB {
            @Override
            ANodeData nodeData(Configuration configuration) {
                return new MariaDbNodeData(configuration.nodeSettings());
            }

            @Override
            AInboxData inbox(ExecutorService executor) {
                return new MariaDbInboxData(executor);
            }

            @Override
            AOrderData orders(ExecutorService executor) {
                return new MariaDbOrderData(TestMappers.items());
            }

            @Override
            ANodeMessageData messages() {
                return new MariaDbNodeMessageData();
            }
        };

        abstract ANodeData nodeData(Configuration configuration);

        abstract AInboxData inbox(ExecutorService executor);

        abstract AOrderData orders(ExecutorService executor);

        abstract ANodeMessageData messages();
    }
}
