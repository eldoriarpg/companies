/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

class SearchQueryTest {

    private boolean matches(SearchQuery query, String materials) {
        return Pattern.compile(query.materialRegex()).matcher(materials).find();
    }

    @Test
    void allMaterialsMatchInAnyOrder() {
        var query = new SearchQuery();
        query.anyMaterial(false);
        query.materials(List.of("DIRT", "STONE"));

        Assertions.assertTrue(matches(query, "STONE DIRT"));
        Assertions.assertTrue(matches(query, "DIRT GRAVEL STONE"));
        Assertions.assertFalse(matches(query, "DIRT GRAVEL"));
    }

    @Test
    void exactMatchRequiresWholeMaterial() {
        var query = new SearchQuery();
        query.exactMatch(true);
        query.materials(List.of("STONE"));

        Assertions.assertTrue(matches(query, "DIRT STONE"));
        Assertions.assertFalse(matches(query, "REDSTONE DIRT"));
    }

    @Test
    void anyMaterialMatchesOneOfThem() {
        var query = new SearchQuery();
        query.materials(List.of("DIRT", "STONE"));

        Assertions.assertTrue(matches(query, "STONE"));
        Assertions.assertFalse(matches(query, "GRAVEL"));
    }

    @Test
    void wordBoundaryCanBeChanged() {
        var query = new SearchQuery();
        query.exactMatch(true);
        query.anyMaterial(false);
        query.materials(List.of("STONE"));

        Assertions.assertEquals("(?=.*\\ySTONE\\y)", query.materialRegex("\\y"));
    }
}
