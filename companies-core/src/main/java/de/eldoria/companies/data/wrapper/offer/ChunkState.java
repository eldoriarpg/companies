/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

/**
 * Where a chunk of a goal is. A chunk stays {@link #OPEN} while its order exists and is not delivered, whether it is
 * waiting to be taken or taken.
 */
public enum ChunkState {
    /**
     * Its order exists and was not delivered yet.
     */
    OPEN,
    /**
     * Its order was delivered; what was delivered counts for the goal.
     */
    DELIVERED,
    /**
     * Its order was removed without being delivered: withdrawn, cancelled or not taken in time. Its amount can be placed
     * again.
     */
    RETURNED
}
