/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Redis with low latency mode as fallback: messages travel over Redis while it is connected, and through the message
 * table while it is not, until Redis is back.
 * <p>
 * Handlers are subscribed to both buses. Publishing goes to the bus in use. The message table is read only while Redis
 * is away; it then starts at the highest id present. Every switch is reported to the listeners with the state of the
 * bus now in use, {@link State#CONNECTED} again when it is connected, so the node drops its caches once, since
 * messages may have been missed during the switch. Reports are made one at a time and always tell the current state,
 * so a report never overtakes a newer one.
 */
public final class FailoverBus implements NodeBus {
    private final NodeBus redis;
    private final MessageTableBus table;
    private final List<Consumer<State>> listeners = new CopyOnWriteArrayList<>();
    private final Object switching = new Object();
    private final Object reporting = new Object();
    private volatile boolean onTable;

    private FailoverBus(NodeBus redis, MessageTableBus table) {
        this.redis = redis;
        this.table = table;
    }

    /**
     * Combines a started Redis bus with a message table bus that is not reading yet. The table is read at once when
     * Redis is not connected.
     *
     * @param redis the Redis bus, started
     * @param table the message table bus, not reading
     * @return the combined bus
     */
    public static FailoverBus start(NodeBus redis, MessageTableBus table) {
        var bus = new FailoverBus(redis, table);
        synchronized (bus.switching) {
            redis.onStateChange(bus::redisChanged);
            table.onStateChange(bus::tableChanged);
            if (!redis.realtime()) bus.useTable();
        }
        return bus;
    }

    private void redisChanged(State state) {
        synchronized (switching) {
            if (state == State.CONNECTED && onTable) {
                onTable = false;
                table.pause();
            } else if (state != State.CONNECTED && !onTable) {
                useTable();
            }
        }
        report();
    }

    private void tableChanged(State state) {
        if (onTable) report();
    }

    private void useTable() {
        onTable = true;
        table.resume();
    }

    private void report() {
        synchronized (reporting) {
            var state = state();
            for (var listener : listeners) listener.accept(state);
        }
    }

    private NodeBus current() {
        return onTable ? table : redis;
    }

    @Override
    public void publish(NodeMessage message) {
        current().publish(message);
    }

    @Override
    public <T extends NodeMessage> void subscribe(Class<T> type, Handler<T> handler) {
        redis.subscribe(type, handler);
        table.subscribe(type, handler);
    }

    @Override
    public void onStateChange(Consumer<State> listener) {
        listeners.add(listener);
    }

    @Override
    public State state() {
        return current().state();
    }

    @Override
    public Transport transport() {
        return current().transport();
    }

    /**
     * @return true while the message table carries the messages because Redis is away
     */
    public boolean fallingBack() {
        return current() == table;
    }

    /**
     * @return the Redis bus
     */
    public NodeBus redis() {
        return redis;
    }

    @Override
    public void close() {
        redis.close();
        table.close();
    }
}
