/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A bus in memory for tests: delivers every published message at once to its own handlers, and lets tests change the
 * state and inject messages of other nodes.
 */
final class LocalBus implements NodeBus {
    private final UUID node;
    private final Map<Class<?>, List<Handler<NodeMessage>>> handlers = new ConcurrentHashMap<>();
    private final List<Consumer<State>> listeners = new CopyOnWriteArrayList<>();
    final List<NodeMessage> published = new CopyOnWriteArrayList<>();
    private volatile State state = State.CONNECTED;
    boolean closed;

    LocalBus(UUID node) {
        this.node = node;
    }

    @Override
    public void publish(NodeMessage message) {
        published.add(message);
        receive(node, message);
    }

    void receive(UUID sender, NodeMessage message) {
        for (var handler : new ArrayList<>(handlers.getOrDefault(message.getClass(), List.of()))) {
            handler.handle(sender, message);
        }
    }

    void change(State next) {
        state = next;
        listeners.forEach(listener -> listener.accept(next));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends NodeMessage> void subscribe(Class<T> type, Handler<T> handler) {
        handlers.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()).add((Handler<NodeMessage>) handler);
    }

    @Override
    public void onStateChange(Consumer<State> listener) {
        listeners.add(listener);
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public void close() {
        closed = true;
    }
}
