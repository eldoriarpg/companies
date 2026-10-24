/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import de.eldoria.companies.dashboard.gateway.GatewayLink;
import de.eldoria.companies.web.api.Message;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A link that records what is sent and answers no request.
 */
public final class RecordingLink implements GatewayLink {
    private final List<Message> sent = new CopyOnWriteArrayList<>();

    @Override
    public boolean send(Message message) {
        sent.add(message);
        return true;
    }

    @Override
    public CompletableFuture<Message> request(Message request, Duration timeout) {
        sent.add(request);
        return new CompletableFuture<>();
    }

    /**
     * The messages of a type, in the order they were sent.
     *
     * @param type the type
     * @param <T>  the type
     * @return the messages
     */
    public <T extends Message> List<T> of(Class<T> type) {
        return sent.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /**
     * @return every message sent
     */
    public List<Message> all() {
        return sent;
    }

    /**
     * Forgets what was sent.
     */
    public void clear() {
        sent.clear();
    }
}
