/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.events.server.ServerEventEndEvent;
import de.eldoria.companies.events.server.ServerEventStartEvent;
import de.eldoria.companies.nodes.NodeMessage.ServerEventChanged;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Fires the API events of a server event that moved, on the node whose move won, see {@link EventMoves}: a
 * {@link ServerEventStartEvent} when it starts and a {@link ServerEventEndEvent} when one that ran is over. An event
 * that ended at its time notes no one as who closed it, one an admin or a plugin ended does. Missed, withdrawn and
 * changed events fire nothing.
 */
public final class EventApiEvents implements BiConsumer<ServerEvent, String> {
    private final Consumer<Event> call;

    /**
     * @param call fires an event, off the main thread
     */
    public EventApiEvents(Consumer<Event> call) {
        this.call = call;
    }

    @Override
    public void accept(ServerEvent event, String change) {
        var fired = of(event, change);
        if (fired != null) call.accept(fired);
    }

    /**
     * @param event  the server event after its move
     * @param change what happened to it, a state or a change of {@link ServerEventChanged}
     * @return the API event of the move, null when the move fires none
     */
    static @Nullable Event of(ServerEvent event, String change) {
        return switch (change) {
            case "ACTIVE" -> new ServerEventStartEvent(event);
            case "ENDED" ->
                new ServerEventEndEvent(
                        event,
                        event.closedBy() == null ? ServerEventEndEvent.Reason.TIME : ServerEventEndEvent.Reason.ENDED);
            case "DEPLETED" -> new ServerEventEndEvent(event, ServerEventEndEvent.Reason.DEPLETED);
            case "CANCELLED" -> new ServerEventEndEvent(event, ServerEventEndEvent.Reason.CANCELLED);
            default -> null;
        };
    }
}
