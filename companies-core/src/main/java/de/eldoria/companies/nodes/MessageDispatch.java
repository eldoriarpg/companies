/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The receiving side every bus shares: the handlers per message type, the state listeners and the decoding of received
 * JSON text. Received messages are handed to the handler executor, one task per handler; messages of another format
 * version or of unknown types are skipped (see {@link NodeMessageCodec}). Thread safe.
 */
final class MessageDispatch {
    private final Executor handlers;
    private final Logger logger;
    private final NodeMessageCodec codec = new NodeMessageCodec();
    private final Map<Class<? extends NodeMessage>, List<NodeBus.Handler<NodeMessage>>> subscriptions =
            new ConcurrentHashMap<>();
    private final List<Consumer<NodeBus.State>> listeners = new CopyOnWriteArrayList<>();

    /**
     * @param handlers runs the handlers of received messages
     * @param logger   reports failing handlers and listeners
     */
    MessageDispatch(Executor handlers, Logger logger) {
        this.handlers = handlers;
        this.logger = logger;
    }

    /**
     * @return the codec of the messages
     */
    NodeMessageCodec codec() {
        return codec;
    }

    /**
     * Calls the handler for every message of a type received from now on.
     *
     * @param type    class of the messages
     * @param handler receives the sending node and the message
     * @param <T>     type of the messages
     */
    @SuppressWarnings("unchecked")
    <T extends NodeMessage> void subscribe(Class<T> type, NodeBus.Handler<T> handler) {
        subscriptions
                .computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>())
                .add((NodeBus.Handler<NodeMessage>) handler);
    }

    /**
     * Calls the listener on every {@link #stateChanged(NodeBus.State)}.
     *
     * @param listener receives the new state
     */
    void onStateChange(Consumer<NodeBus.State> listener) {
        listeners.add(listener);
    }

    /**
     * Tells every listener about a new state.
     *
     * @param state the new state
     */
    void stateChanged(NodeBus.State state) {
        for (var listener : listeners) {
            try {
                listener.accept(state);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not apply the new state of the node bus.", e);
            }
        }
    }

    /**
     * Decodes a received message and hands it to its handlers.
     *
     * @param text the JSON text
     */
    void receive(String text) {
        var envelope = codec.decode(text);
        if (envelope.isEmpty()) return;
        var message = envelope.get().message();
        for (var handler : subscriptions.getOrDefault(message.getClass(), List.of())) {
            try {
                handlers.execute(() -> handle(handler, envelope.get().node(), message));
            } catch (RejectedExecutionException e) {
                return;
            }
        }
    }

    private void handle(NodeBus.Handler<NodeMessage> handler, UUID sender, NodeMessage message) {
        try {
            handler.handle(sender, message);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not handle the node message " + message + ".", e);
        }
    }
}
