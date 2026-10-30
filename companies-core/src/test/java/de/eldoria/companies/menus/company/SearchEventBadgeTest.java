/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.commands.company.order.search.SearchResults;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.services.events.EventPreviews.EventMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchEventBadgeTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anOrderAnEventBoostsCarriesItsFactorAndTaxAndOthersNothing() {
        var boosted = new SimpleOrder(UUID.randomUUID(), "boosted", 0).toFullOrder(List.of());
        var results = SearchResults.empty().withEvents(Map.of(-1, new EventMark(1.5, 5, 0, null)));

        var badge = SearchResultsMenu.eventBadge(boosted, results, "o").build();
        var none = SearchResultsMenu.eventBadge(boosted, SearchResults.empty(), "o")
                .build();
        var feeOnly = SearchResultsMenu.eventBadge(
                        boosted, SearchResults.empty().withEvents(Map.of(-1, new EventMark(1, 0, 50, null))), "o")
                .build();

        assertTrue(badge.contains("×<o_factor>") && badge.contains("events.mark.tax"), badge);
        assertEquals("", none);
        assertEquals("", feeOnly);
    }
}
