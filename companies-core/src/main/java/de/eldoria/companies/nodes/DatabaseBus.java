/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import java.util.function.Consumer;

/**
 * The bus without a message server: nodes learn about changes only through the database, by polling and when caches
 * expire. Publishing does nothing and no message ever arrives; this is the behaviour of a network without Redis.
 */
public final class DatabaseBus implements NodeBus {
    @Override
    public void publish(NodeMessage message) {}

    @Override
    public <T extends NodeMessage> void subscribe(Class<T> type, Handler<T> handler) {}

    @Override
    public void onStateChange(Consumer<State> listener) {}

    @Override
    public State state() {
        return State.OFF;
    }

    @Override
    public void close() {}
}
