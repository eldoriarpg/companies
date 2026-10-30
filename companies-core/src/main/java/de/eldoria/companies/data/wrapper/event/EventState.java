/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.event;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a server event is in its life. The first two are running states, the others are final.
 */
public enum EventState {
    /**
     * Waiting for its start.
     */
    SCHEDULED,
    /**
     * Running: its effects apply.
     */
    ACTIVE,
    /**
     * Its end passed, or an admin ended it early. A scheduled event whose whole time passed while the primary was down
     * ends without starting, notes {@link ServerEvent#MISSED_BY} as who closed it, and its change is signalled as
     * {@link de.eldoria.companies.nodes.NodeMessage.ServerEventChanged#MISSED} instead of {@code ENDED}, so it is not
     * announced.
     */
    ENDED,
    /**
     * Its funding could not pay its part of a payout anymore.
     */
    DEPLETED,
    /**
     * Cancelled by an admin, before or while it ran. Only one that ran is announced as cancelled; one cancelled before
     * its start is signalled as {@link de.eldoria.companies.nodes.NodeMessage.ServerEventChanged#WITHDRAWN}.
     */
    CANCELLED;

    /**
     * The states an event can still change from.
     */
    public static final Set<EventState> RUNNING = EnumSet.of(SCHEDULED, ACTIVE);

    /**
     * @return whether the event is over for good
     */
    public boolean closed() {
        return !RUNNING.contains(this);
    }
}
