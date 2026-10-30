/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.server;

import de.eldoria.companies.components.event.IServerEvent;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A server event started: its effects apply from now on.
 * <p>
 * Fired off the main thread, once in the whole network, on the server that started it, which is the primary server for
 * events started at their time. Events that never start, because their whole time passed while the primary server was
 * down or they were withdrawn, fire nothing.
 */
public class ServerEventStartEvent extends Event {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final IServerEvent event;

    /**
     * Creates a new event.
     *
     * @param event the server event, as it is now
     */
    public ServerEventStartEvent(IServerEvent event) {
        super(true);
        this.event = Objects.requireNonNull(event, "event");
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * The server event that started.
     *
     * @return the event
     */
    public IServerEvent event() {
        return event;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
