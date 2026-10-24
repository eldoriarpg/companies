/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import de.eldoria.companies.dashboard.gateway.GatewayLink;
import de.eldoria.companies.web.api.Message;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * A link handed to the parts built before the {@link Dashboard} that owns the connection exists. It drops messages
 * until {@link #use(GatewayLink)} names the target.
 */
final class LinkProxy implements GatewayLink {
    private volatile @Nullable GatewayLink target;

    /**
     * Sends through the target from now on.
     *
     * @param link the target
     */
    void use(GatewayLink link) {
        target = link;
    }

    @Override
    public boolean send(Message message) {
        var current = target;
        return current != null && current.send(message);
    }

    @Override
    public CompletableFuture<Message> request(Message request, Duration timeout) {
        var current = target;
        if (current == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("The dashboard is not connected."));
        }
        return current.request(request, timeout);
    }
}
