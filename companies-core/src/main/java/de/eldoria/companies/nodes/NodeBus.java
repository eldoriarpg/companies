/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Carries {@link NodeMessage}s between the nodes of a network.
 * <p>
 * Every published message reaches every subscribed node, the sender included. While the bus is not
 * {@link #realtime()}, messages may be lost and the pollers and cache lifetimes stay in charge, exactly as without a
 * bus. Handlers run off the main thread; a handler touching Bukkit moves that work to the main thread itself.
 */
public interface NodeBus extends AutoCloseable {
    /**
     * Sends a message to every node. Never blocks; a message that cannot be sent is dropped.
     *
     * @param message the message
     */
    void publish(NodeMessage message);

    /**
     * Calls the handler for every message of a type that arrives from now on.
     *
     * @param type    class of the messages
     * @param handler receives the sending node and the message
     * @param <T>     type of the messages
     */
    <T extends NodeMessage> void subscribe(Class<T> type, Handler<T> handler);

    /**
     * Calls the listener whenever the state changes, on the thread noticing the change.
     *
     * @param listener receives the new state
     */
    void onStateChange(Consumer<State> listener);

    /**
     * @return the current state
     */
    State state();

    /**
     * Whether messages arrive at once right now, so polls and cache lifetimes are only a safety net.
     *
     * @return true while connected
     */
    default boolean realtime() {
        return state() == State.CONNECTED;
    }

    /**
     * Stops sending and receiving.
     */
    @Override
    void close();

    /**
     * Handles a message of one type.
     *
     * @param <T> type of the message
     */
    @FunctionalInterface
    interface Handler<T extends NodeMessage> {
        /**
         * @param node    uid of the node that sent the message
         * @param message the message
         */
        void handle(UUID node, T message);
    }

    /**
     * Whether a bus delivers messages.
     */
    enum State {
        /**
         * No message bus is configured; the database alone connects the nodes.
         */
        OFF,
        /**
         * Messages arrive at once.
         */
        CONNECTED,
        /**
         * The connection is lost or was never made and is being retried; the nodes fall back to polling meanwhile.
         */
        DISCONNECTED
    }
}
