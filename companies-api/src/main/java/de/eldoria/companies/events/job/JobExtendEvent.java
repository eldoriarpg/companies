/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.job;

import de.eldoria.companies.components.job.IJob;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * The owner gave a taken job more time, on their own or when its company asked. Fired on the server where the owner
 * did it.
 */
public class JobExtendEvent extends JobEvent {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final int hours;

    /**
     * Creates a new event, asynchronous when created off the main thread.
     *
     * @param job   the job, with its new due time
     * @param hours the hours added
     */
    public JobExtendEvent(IJob job, int hours) {
        super(job);
        this.hours = hours;
    }

    /**
     * The hours added.
     *
     * @return hours
     */
    public int hours() {
        return hours;
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
