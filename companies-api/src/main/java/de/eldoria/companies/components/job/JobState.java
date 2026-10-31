/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.job;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a job is in its life.
 */
public enum JobState {
    /**
     * Waiting for a company to take it.
     */
    OPEN,
    /**
     * A company works on it and has access to its area.
     */
    TAKEN,
    /**
     * The company says the work is done and waits for the owner to approve or reject it.
     */
    DONE,
    /**
     * The owner or the company opened a dispute; access is frozen until an admin settles it.
     */
    DISPUTED,
    /**
     * The work checked out or was approved and the company was paid.
     */
    PAID,
    /**
     * An admin settled a dispute, splitting the price between the company and the owner.
     */
    SETTLED,
    /**
     * It ended unfinished: the owner cancelled it while it was open, or its land changed hands. A charged job's owner
     * got back what was not earned.
     */
    CANCELLED,
    /**
     * Nobody took it before it expired. A charged job's owner got price and fee back.
     */
    EXPIRED;

    /**
     * The states a job can still change from.
     */
    public static final Set<JobState> RUNNING = EnumSet.of(OPEN, TAKEN, DONE, DISPUTED);

    /**
     * The states in which a company holds the job. A job its owner disputes uses none of the company's work slots, so an
     * owner can not block them; one the company disputes keeps its slot.
     */
    public static final Set<JobState> HELD = EnumSet.of(TAKEN, DONE, DISPUTED);

    /**
     * Whether the job is over for good.
     *
     * @return whether the job is over for good
     */
    public boolean closed() {
        return !RUNNING.contains(this);
    }
}
