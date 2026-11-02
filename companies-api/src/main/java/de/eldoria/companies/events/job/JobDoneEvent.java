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
 * The company working on a contract said it is done; its owner approves or rejects it next. Fired on the server where
 * the member said it.
 */
public class JobDoneEvent extends JobEvent {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    /**
     * Creates a new event, asynchronous when created off the main thread.
     *
     * @param job the contract, waiting for its owner
     */
    public JobDoneEvent(IJob job) {
        super(job);
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
