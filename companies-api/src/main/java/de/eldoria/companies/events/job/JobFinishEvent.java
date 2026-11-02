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
 * The company working on a dig, terraform or build job finished it: its area checked out and the company was paid.
 * Fired on the server where a member finished it, followed by {@link JobEndEvent} with {@link JobEndEvent.Reason#PAID}.
 */
public class JobFinishEvent extends JobEvent {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final double paid;

    /**
     * Creates a new event, asynchronous when created off the main thread.
     *
     * @param job  the job as it was before it was paid
     * @param paid what its workers were paid in all
     */
    public JobFinishEvent(IJob job, double paid) {
        super(job);
        this.paid = paid;
    }

    /**
     * What the workers were paid in all.
     *
     * @return money
     */
    public double paid() {
        return paid;
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
