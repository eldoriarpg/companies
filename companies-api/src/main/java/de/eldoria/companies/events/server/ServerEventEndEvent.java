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
 * A server event that ran is over: its effects no longer apply.
 * <p>
 * Fired off the main thread, once in the whole network, on the server that ended it. An event cancelled or withdrawn
 * before its start, and one whose whole time passed while the primary server was down, fires nothing.
 */
public class ServerEventEndEvent extends Event {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final IServerEvent event;
    private final Reason reason;

    /**
     * Creates a new event.
     *
     * @param event  the server event, as it is now
     * @param reason why it is over
     */
    public ServerEventEndEvent(IServerEvent event, Reason reason) {
        super(true);
        this.event = Objects.requireNonNull(event, "event");
        this.reason = Objects.requireNonNull(reason, "reason");
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
     * The server event that is over.
     *
     * @return the event
     */
    public IServerEvent event() {
        return event;
    }

    /**
     * Why the event is over.
     *
     * @return reason
     */
    public Reason reason() {
        return reason;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Why a server event is over.
     */
    public enum Reason {
        /**
         * Its end passed.
         */
        TIME,
        /**
         * An admin or a plugin ended it early.
         */
        ENDED,
        /**
         * Its money ran out.
         */
        DEPLETED,
        /**
         * An admin or a plugin cancelled it while it ran.
         */
        CANCELLED
    }
}
