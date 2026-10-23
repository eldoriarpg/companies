/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.configuration.EnvReference;
import de.eldoria.companies.configuration.elements.DatabaseSettings;
import de.eldoria.companies.data.repository.ANodeMessageData;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

/**
 * Picks the bus of this node when it starts. A network runs exactly one of them, without switching to another one
 * while it runs; when several are enabled, the first of this list is used and one warning names the others:
 * <ol>
 *     <li>Redis, when it is enabled. The {@link RedisBus} falls back to polling by itself while the server cannot be
 *     reached and keeps retrying. When the Redis password names an environment variable that is not set, Redis is not
 *     used and the node polls.</li>
 *     <li>PostgreSQL notify, the {@link PostgresNotifyBus}, when {@code messaging.postgresNotify} is on and the storage
 *     type is {@code POSTGRES}. It falls back to polling by itself while it cannot listen. With another storage type
 *     the setting is ignored with a warning.</li>
 *     <li>Low latency mode, the {@link MessageTableBus}, when {@code messaging.lowLatency} is on.</li>
 *     <li>The {@link DatabaseBus}: polling only.</li>
 * </ol>
 */
public final class NodeBuses {
    private static final Duration START_WAIT = Duration.ofSeconds(3);
    private static final int DEFAULT_POSTGRES_PORT = 5432;
    private static final String FALLBACK_NOTE = "notifications.pollSeconds, placeholders.refreshSeconds and"
            + " top.cacheSeconds are only a slow safety fallback now.";

    private NodeBuses() {}

    /**
     * Creates the bus of this node.
     *
     * @param settings    the settings of {@code database.yml}
     * @param node        uid of this node
     * @param primary     true on the primary node, which deletes old rows of the message table
     * @param messages    the message table
     * @param handlers    runs the handlers of received messages
     * @param environment reads an environment variable for an {@code env:} password
     * @param logger      reports the choice and connection problems
     * @return the bus, connected, retrying or the database bus
     */
    public static NodeBus create(
            DatabaseSettings settings,
            UUID node,
            boolean primary,
            ANodeMessageData messages,
            Executor handlers,
            UnaryOperator<String> environment,
            Logger logger) {
        var transport = choose(settings, logger);
        return switch (transport) {
            case REDIS -> redis(settings, node, handlers, environment, logger);
            case POSTGRES_NOTIFY -> postgresNotify(settings, node, handlers, logger);
            case LOW_LATENCY -> {
                logger.info("Low latency mode is on: the servers exchange messages through the database every"
                        + " second. " + FALLBACK_NOTE);
                yield MessageTableBus.start(messages, node, primary, handlers, logger, MessageTableBus.Timings.DEFAULT);
            }
            case DATABASE -> new DatabaseBus();
        };
    }

    /**
     * Picks the transport from the settings and warns about the enabled ones that are ignored.
     *
     * @param settings the settings of {@code database.yml}
     * @param logger   receives the warnings
     * @return the transport to use
     */
    static NodeBus.Transport choose(DatabaseSettings settings, Logger logger) {
        var messaging = settings.messaging();
        var postgresNotify = messaging.postgresNotify();
        if (postgresNotify && settings.storageType() != DatabaseSettings.StorageType.POSTGRES) {
            logger.warning("messaging.postgresNotify needs the storage type POSTGRES, but this server uses "
                    + settings.storageType() + ". PostgreSQL notify is ignored.");
            postgresNotify = false;
        }
        List<NodeBus.Transport> enabled = new ArrayList<>();
        if (settings.redis().enabled()) enabled.add(NodeBus.Transport.REDIS);
        if (postgresNotify) enabled.add(NodeBus.Transport.POSTGRES_NOTIFY);
        if (messaging.lowLatency()) enabled.add(NodeBus.Transport.LOW_LATENCY);
        if (enabled.isEmpty()) return NodeBus.Transport.DATABASE;
        var used = enabled.getFirst();
        if (enabled.size() > 1) {
            var ignored = enabled.subList(1, enabled.size());
            logger.warning(names(enabled) + (enabled.size() == 2 ? " are both" : " are all")
                    + " enabled in database.yml, but a server uses only one of them. "
                    + capitalized(names(ignored)) + (ignored.size() == 1 ? " is" : " are") + " ignored while "
                    + name(used) + " is enabled.");
        }
        return used;
    }

    private static String names(List<NodeBus.Transport> transports) {
        var names = transports.stream().map(NodeBuses::name).toList();
        if (names.size() == 1) return names.getFirst();
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
    }

    private static String name(NodeBus.Transport transport) {
        return switch (transport) {
            case REDIS -> "Redis";
            case POSTGRES_NOTIFY -> "PostgreSQL notify";
            case LOW_LATENCY -> "low latency mode";
            case DATABASE -> "database polling";
        };
    }

    private static String capitalized(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static NodeBus redis(
            DatabaseSettings database, UUID node, Executor handlers, UnaryOperator<String> environment, Logger logger) {
        var settings = database.redis();
        var password = EnvReference.resolve(
                settings.password(),
                environment,
                name -> logger.warning(
                        "The environment variable " + name + " for the Redis password is not set. Redis is not used."));
        if (password.isEmpty()) return new DatabaseBus();
        var server = new RedisBus.Server(
                settings.host(),
                settings.port(),
                settings.user(),
                password.get(),
                settings.ssl(),
                settings.database(),
                database.channelPrefix());
        return RedisBus.start(server, node, handlers, logger, START_WAIT);
    }

    private static NodeBus postgresNotify(DatabaseSettings settings, UUID node, Executor handlers, Logger logger) {
        var channel = settings.channelPrefix();
        if (!PostgresNotifyBus.fits(channel)) {
            logger.warning("messaging.channelPrefix is longer than the " + PostgresNotifyBus.MAX_CHANNEL_BYTES
                    + " bytes PostgreSQL allows for a channel name. PostgreSQL notify is not used.");
            return new DatabaseBus();
        }
        logger.info("PostgreSQL notify is on: the servers exchange messages with LISTEN and NOTIFY. " + FALLBACK_NOTE);
        var server = new PostgresNotifyBus.Server(
                settings.host(),
                port(settings.port()),
                settings.database(),
                settings.user(),
                settings.password(),
                channel);
        return PostgresNotifyBus.start(server, node, PostgresNotifyBus.Notifier.pooled(), handlers, logger, START_WAIT);
    }

    private static int port(String port) {
        try {
            return Integer.parseInt(port.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return DEFAULT_POSTGRES_PORT;
        }
    }
}
