/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.job;

import de.eldoria.companies.components.job.IJob;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A job ended, for good or for the company that worked on it.
 * <p>
 * Fired once in the whole network, on the server where it happened: where a member finished or gave it back, where the
 * owner approved, cancelled or ended it, where an admin settled it, the primary server when it expired, ran out of time
 * or was approved without an answer, and the server holding its area when its land changed hands. A job given back or
 * out of time is open again afterwards; with every other reason it is closed.
 */
public class JobEndEvent extends JobEvent {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final Reason reason;

    /**
     * Creates a new event, asynchronous when created off the main thread.
     *
     * @param job    the job as it was before it ended
     * @param reason why it ended
     */
    public JobEndEvent(IJob job, Reason reason) {
        super(job);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Why the job ended.
     *
     * @return reason
     */
    public Reason reason() {
        return reason;
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

    /**
     * Why a job ended.
     */
    public enum Reason {
        /**
         * Its company was paid: an area job checked out, a contract was approved, or a harvest job ended.
         */
        PAID,
        /**
         * An admin settled its dispute.
         */
        SETTLED,
        /**
         * The owner cancelled it while it was open.
         */
        CANCELLED,
        /**
         * Nobody took it in time.
         */
        EXPIRED,
        /**
         * Its company gave it back or was disbanded; it is open again.
         */
        GIVEN_BACK,
        /**
         * Its company ran out of time; it is open again.
         */
        OVERDUE,
        /**
         * Its land changed hands.
         */
        LAND_LOST
    }
}
