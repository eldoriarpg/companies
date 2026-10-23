/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCursorTest {
    private static final long SECOND = Duration.ofSeconds(1).toNanos();

    private final MessageCursor cursor = new MessageCursor(10, Duration.ofSeconds(10));

    @Test
    void rowsInOrderMoveTheCursor() {
        assertEquals(10, cursor.readFrom());

        assertTrue(cursor.accept(11, 0));
        assertTrue(cursor.accept(12, 0));

        assertEquals(12, cursor.readFrom());
        assertFalse(cursor.accept(12, 0));
    }

    @Test
    void aSkippedIdIsReadAgainAndDeliveredOnce() {
        assertTrue(cursor.accept(13, 0));

        assertEquals(10, cursor.readFrom());
        assertFalse(cursor.accept(13, 0));
        assertTrue(cursor.accept(12, 0));
        assertFalse(cursor.accept(12, 0));
        assertEquals(10, cursor.readFrom());
        assertTrue(cursor.accept(11, 0));

        assertEquals(13, cursor.readFrom());
    }

    @Test
    void aSkippedIdIsGivenUpAfterTheGapWait() {
        cursor.accept(12, 0);

        cursor.expire(5 * SECOND);
        assertEquals(10, cursor.readFrom());

        cursor.expire(11 * SECOND);
        assertEquals(12, cursor.readFrom());
        assertFalse(cursor.accept(11, 11 * SECOND));
    }

    @Test
    void aHugeJumpKeepsABoundedNumberOfGaps() {
        assertTrue(cursor.accept(1_000_000, 0));

        assertEquals(10, cursor.readFrom());
        cursor.expire(11 * SECOND);
        assertEquals(1_000_000, cursor.readFrom());
        assertEquals(1_000_000, cursor.last());
    }
}
