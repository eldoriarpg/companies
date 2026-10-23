/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.configuration.EnvReference;
import de.eldoria.companies.configuration.elements.RedisSettings;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

/**
 * Picks the bus of this node when it starts.
 * <p>
 * Without Redis, or when the Redis password names an environment variable that is not set, the node runs on the
 * {@link DatabaseBus}. Otherwise it runs on the {@link RedisBus}, which falls back to polling by itself while the
 * server cannot be reached and keeps retrying.
 */
public final class NodeBuses {
    private static final Duration START_WAIT = Duration.ofSeconds(3);

    private NodeBuses() {}

    /**
     * Creates the bus of this node.
     *
     * @param settings    the {@code redis} section of {@code database.yml}
     * @param node        uid of this node
     * @param handlers    runs the handlers of received messages
     * @param environment reads an environment variable for an {@code env:} password
     * @param logger      reports the choice and connection problems
     * @return the bus, connected, retrying or the database bus
     */
    public static NodeBus create(
            RedisSettings settings, UUID node, Executor handlers, UnaryOperator<String> environment, Logger logger) {
        if (!settings.enabled()) return new DatabaseBus();
        var password = EnvReference.resolve(
                settings.password(),
                environment,
                name -> logger.warning("The environment variable " + name
                        + " for the Redis password is not set. Redis is not used; notifications and caches use"
                        + " polling."));
        if (password.isEmpty()) return new DatabaseBus();
        var connection = new RedisBus.Connection(
                settings.host(),
                settings.port(),
                settings.user(),
                password.get(),
                settings.ssl(),
                settings.database(),
                settings.channelPrefix());
        return RedisBus.start(connection, node, handlers, logger, START_WAIT);
    }
}
