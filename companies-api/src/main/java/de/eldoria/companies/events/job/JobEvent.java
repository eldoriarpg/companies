/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.job;

import de.eldoria.companies.components.job.IJob;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import java.util.Objects;

/**
 * An event about a job. It is asynchronous when fired off the main thread.
 */
public abstract class JobEvent extends Event {
    private final IJob job;

    /**
     * Creates a new event, asynchronous when created off the main thread.
     *
     * @param job the job
     */
    protected JobEvent(IJob job) {
        super(!Bukkit.isPrimaryThread());
        this.job = Objects.requireNonNull(job, "job");
    }

    /**
     * The job.
     *
     * @return the job as it was when the event was fired
     */
    public IJob job() {
        return job;
    }
}
