/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTest {
    private static final List<Integer> ENTRIES = IntStream.range(0, 25).boxed().toList();

    @Test
    void anEmptyListHasOneEmptyPage() {
        var page = Page.of(List.of(), 3, 10);

        assertEquals(List.of(), page.items());
        assertEquals(0, page.number());
        assertEquals(1, page.count());
        assertFalse(page.hasPrevious());
        assertFalse(page.hasNext());
    }

    @Test
    void aMiddlePageHasNeighbours() {
        var page = Page.of(ENTRIES, 1, 10);

        assertEquals(IntStream.range(10, 20).boxed().toList(), page.items());
        assertEquals(3, page.count());
        assertTrue(page.hasPrevious());
        assertTrue(page.hasNext());
    }

    @Test
    void theLastPageHoldsTheRest() {
        var page = Page.of(ENTRIES, 2, 10);

        assertEquals(List.of(20, 21, 22, 23, 24), page.items());
        assertFalse(page.hasNext());
    }

    @Test
    void pagesOutOfRangeShowTheNearestPage() {
        assertEquals(2, Page.of(ENTRIES, 7, 10).number());
        assertEquals(0, Page.of(ENTRIES, -1, 10).number());
    }

    @Test
    void anExactlyFullListHasNoEmptyLastPage() {
        assertEquals(2, Page.of(ENTRIES.subList(0, 20), 0, 10).count());
    }

    @Test
    void theSizeMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> Page.of(ENTRIES, 0, 0));
    }
}
