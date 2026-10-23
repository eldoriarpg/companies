/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.configuration.EnvReference;
import de.eldoria.companies.configuration.elements.DatabaseSettings;
import de.eldoria.companies.configuration.elements.RedisSettings;
import de.eldoria.companies.data.repository.ANodeMessageData;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

/**
 * Picks the bus of this node when it starts, in this order:
 * <ol>
 *     <li>Redis, when it is enabled and its password can be resolved. The {@link RedisBus} falls back to polling by
 *     itself while the server cannot be reached and keeps retrying. With low latency mode on as well, the
 *     {@link FailoverBus} uses the message table instead of polling meanwhile.</li>
 *     <li>Low latency mode, the {@link MessageTableBus}, when {@code messaging.lowLatency} is on.</li>
 *     <li>The {@link DatabaseBus}: polling only.</li>
 * </ol>
 */
public final class NodeBuses {
    private static final Duration START_WAIT = Duration.ofSeconds(3);

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
        var lowLatency = settings.messaging().lowLatency();
        if (lowLatency) {
            logger.info("Low latency mode is on: the servers exchange messages through the database every second."
                    + " notifications.pollSeconds, placeholders.refreshSeconds and top.cacheSeconds are only a slow"
                    + " safety fallback now.");
        }
        var redis = redis(settings.redis(), node, handlers, environment, logger);
        if (redis.isPresent()) {
            if (!lowLatency) return redis.get();
            var table =
                    MessageTableBus.create(messages, node, primary, handlers, logger, MessageTableBus.Timings.DEFAULT);
            return FailoverBus.start(redis.get(), table);
        }
        if (lowLatency) {
            return MessageTableBus.start(messages, node, primary, handlers, logger, MessageTableBus.Timings.DEFAULT);
        }
        return new DatabaseBus();
    }

    private static Optional<NodeBus> redis(
            RedisSettings settings, UUID node, Executor handlers, UnaryOperator<String> environment, Logger logger) {
        if (!settings.enabled()) return Optional.empty();
        var password = EnvReference.resolve(
                settings.password(),
                environment,
                name -> logger.warning(
                        "The environment variable " + name + " for the Redis password is not set. Redis is not used."));
        if (password.isEmpty()) return Optional.empty();
        var server = new RedisBus.Server(
                settings.host(),
                settings.port(),
                settings.user(),
                password.get(),
                settings.ssl(),
                settings.database(),
                settings.channelPrefix());
        return Optional.of(RedisBus.start(server, node, handlers, logger, START_WAIT));
    }
}
