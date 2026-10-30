/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.job;

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
     * The work checked out and the company was paid.
     */
    PAID,
    /**
     * The owner cancelled it while it was open and got the price back.
     */
    CANCELLED,
    /**
     * Nobody took it before it expired; the owner got the price back.
     */
    EXPIRED;

    /**
     * The states a job can still change from.
     */
    public static final Set<JobState> RUNNING = EnumSet.of(OPEN, TAKEN);

    /**
     * @return whether the job is over for good
     */
    public boolean closed() {
        return !RUNNING.contains(this);
    }
}
