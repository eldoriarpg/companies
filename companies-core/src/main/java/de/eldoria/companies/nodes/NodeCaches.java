/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The caches of this node that other nodes can invalidate, by name.
 * <p>
 * A cache registers how to drop a key, or everything when no key is given, under a name; several caches may share a
 * name. Every invalidator must be thread safe or move its work to the right thread itself. A failing invalidator is
 * logged and does not stop the others.
 */
public final class NodeCaches {
    /**
     * Companies, their members, profiles, levels and treasuries. Key: the company id.
     */
    public static final String COMPANY = "company";
    /**
     * Values of a single player, such as the orders they work on alone. Key: the player's uuid.
     */
    public static final String PLAYER = "player";
    /**
     * Single orders. Key: the order id.
     */
    public static final String ORDER = "order";
    /**
     * Market prices of materials. No key.
     */
    public static final String PRICES = "prices";
    /**
     * Rankings and the placeholders reading them. No key.
     */
    public static final String RANKING = "ranking";
    /**
     * Open order counts on board signs. No key.
     */
    public static final String BOARDS = "boards";
    /**
     * The dashboard key of the network. Key: {@value #DASHBOARD_KEY}, sent after a new key was stored; the other nodes
     * connect again with it.
     */
    public static final String DASHBOARD = "dashboard";
    /**
     * The key of {@link #DASHBOARD} that says a new dashboard key was stored.
     */
    public static final String DASHBOARD_KEY = "key";

    private final Map<String, List<Consumer<Optional<String>>>> invalidators = new ConcurrentHashMap<>();
    private final Logger logger;

    /**
     * @param logger reports failing invalidators
     */
    public NodeCaches(Logger logger) {
        this.logger = logger;
    }

    /**
     * Registers a cache under a name.
     *
     * @param name        name of the cache
     * @param invalidator drops a key, or every value when the key is empty
     */
    public void register(String name, Consumer<Optional<String>> invalidator) {
        invalidators
                .computeIfAbsent(name, ignored -> new CopyOnWriteArrayList<>())
                .add(invalidator);
    }

    /**
     * Drops a key, or every value, of every cache with a name. Unknown names are ignored.
     *
     * @param name name of the caches
     * @param key  key to drop, empty for every value
     */
    public void invalidate(String name, Optional<String> key) {
        for (var invalidator : invalidators.getOrDefault(name, List.of())) {
            try {
                invalidator.accept(key);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not invalidate the cache " + name + ".", e);
            }
        }
    }

    /**
     * Drops every value of every cache, after messages may have been missed.
     */
    public void clear() {
        for (var name : invalidators.keySet()) {
            invalidate(name, Optional.empty());
        }
    }
}
