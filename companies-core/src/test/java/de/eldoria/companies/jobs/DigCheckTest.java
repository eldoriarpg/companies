/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.job.JobArea;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The check of a dig job: every block cleared, the highest one left named, and the share cleared.
 */
class DigCheckTest {
    private static final JobArea AREA = new JobArea("world", 0, 60, 0, 1, 61, 1);

    @Test
    void anAreaOfOnlyClearedBlocksIsDone() {
        var progress = DigCheck.progress(AREA, (x, y, z) -> true);

        assertTrue(progress.done());
        assertEquals(8, progress.cleared());
        assertEquals(100, progress.percent());
    }

    @Test
    void theHighestBlockLeftIsNamed() {
        var progress = DigCheck.progress(AREA, (x, y, z) -> !(x == 1 && z == 0));

        assertFalse(progress.done());
        assertEquals(6, progress.cleared());
        assertArrayEquals(new int[] {1, 61, 0}, progress.left());
        assertEquals("1 61 0", progress.leftText());
        assertEquals(75, progress.percent());
    }

    @Test
    void anAlmostClearedAreaIsNeverShownAsWhole() {
        var area = new JobArea("world", 0, 0, 0, 999, 0, 0);

        var progress = DigCheck.progress(area, (x, y, z) -> x != 500);

        assertEquals(99, progress.percent());
    }
}
