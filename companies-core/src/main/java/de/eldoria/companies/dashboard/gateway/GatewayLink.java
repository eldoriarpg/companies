/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.gateway;

import de.eldoria.companies.web.api.Message;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Sends messages to the gateway over the node's connection.
 */
public interface GatewayLink {
    /**
     * Sends a message without waiting for it to leave.
     *
     * @param message the message
     * @return false when the node is not connected and the message was dropped
     */
    boolean send(Message message);

    /**
     * Sends a request and waits for the message carrying its id, the answer or an error.
     *
     * @param request the request, with an id
     * @param timeout how long to wait for the answer
     * @return the answer; fails when the node is not connected, the connection closes or the time runs out
     */
    CompletableFuture<Message> request(Message request, Duration timeout);
}
