/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.job;

import de.eldoria.companies.components.job.IJob;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A player is about to post a job, in game, on the dashboard or through the API.
 * <p>
 * Fired on the server that holds the job's area, after every check passed and before any money is taken. The job is
 * not stored yet, so its id is 0. Cancelling it keeps the job from being posted. It may still not be posted when the
 * owner can not pay or it can not be stored.
 */
public class JobPostEvent extends CancellableJobEvent implements PlayerProvider<OfflinePlayer> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer owner;

    /**
     * Creates a new event, asynchronous when created off the main thread.
     *
     * @param job   the job, not stored yet
     * @param owner the player posting it
     */
    public JobPostEvent(IJob job, OfflinePlayer owner) {
        super(job);
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /**
     * The player posting the job.
     *
     * @return owner
     */
    @Override
    public OfflinePlayer player() {
        return owner;
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
