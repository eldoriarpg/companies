/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.job;

import de.eldoria.companies.components.job.IJob;
import org.bukkit.event.Cancellable;

/**
 * A job event fired after every check passed and before anything is stored; cancelling it stops the change, and the
 * player sees that another plugin prevented it.
 */
public abstract class CancellableJobEvent extends JobEvent implements Cancellable {
    private boolean cancelled;

    /**
     * Creates a new event, asynchronous when created off the main thread.
     *
     * @param job the job
     */
    protected CancellableJobEvent(IJob job) {
        super(job);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }
}
