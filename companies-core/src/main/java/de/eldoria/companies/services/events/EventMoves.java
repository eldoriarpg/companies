/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.nodes.NodeMessage.ServerEventChanged;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * What the node that moved a server event does once in the whole network: it logs the move and posts it, such as to
 * the Discord webhook. Only the node whose conditional move won signals it, see {@link ServerEventService}, so a move
 * another node won is neither logged nor posted here.
 */
public final class EventMoves implements Consumer<ServerEventChanged> {
    private final AServerEventData data;
    private final BiConsumer<ServerEvent, String> post;
    private final Logger logger;

    /**
     * @param data   event repository, read for the event that moved
     * @param post   posts the event with what happened to it
     * @param logger logs the move
     */
    public EventMoves(AServerEventData data, BiConsumer<ServerEvent, String> post, Logger logger) {
        this.data = data;
        this.post = post;
        this.logger = logger;
    }

    @Override
    public void accept(ServerEventChanged message) {
        var event = data.event(message.event()).orElse(null);
        if (event == null) return;
        logger.info("The server event #" + event.id() + " (" + event.name() + ") changed: " + message.change() + ".");
        try {
            post.accept(event, message.change());
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not post the change of server event " + event.id(), e);
        }
    }
}
