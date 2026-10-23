/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import de.eldoria.companies.commands.company.order.search.SearchQuery;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The orders a board shows, with the semantics of the order search: the name is contained in the order name, the
 * materials match by part or exactly and any or all of them have to be ordered, and the ranges are inclusive.
 *
 * @param name        part of the order name, or null for any name
 * @param materials   material names in lower case, empty for any material
 * @param anyMaterial whether one of the materials is enough, instead of all of them
 * @param exactMatch  whether a material has to match the whole name instead of a part of it
 * @param minPrice    lowest order price
 * @param maxPrice    highest order price
 * @param minSize     smallest number of items
 * @param maxSize     largest number of items
 */
public record BoardFilter(
        @Nullable String name,
        List<String> materials,
        boolean anyMaterial,
        boolean exactMatch,
        double minPrice,
        double maxPrice,
        int minSize,
        int maxSize) {
    /**
     * Every open order.
     */
    public static final BoardFilter ALL =
            new BoardFilter(null, List.of(), true, false, 0.0, Double.MAX_VALUE, 0, Integer.MAX_VALUE);

    /**
     * Lowers the material names and copies them.
     */
    public BoardFilter {
        materials = materials.stream()
                .map(material -> material.toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * Orders with any of the materials, matched by a part of their name.
     *
     * @param materials material names or parts of them
     * @return the filter
     */
    public static BoardFilter ofMaterials(List<String> materials) {
        return new BoardFilter(null, materials, true, false, 0.0, Double.MAX_VALUE, 0, Integer.MAX_VALUE);
    }

    /**
     * A new search query with this filter, sorted by age.
     *
     * @return the query
     */
    public SearchQuery toQuery() {
        var query = new SearchQuery();
        query.name(name);
        query.materials(new ArrayList<>(materials));
        query.anyMaterial(anyMaterial);
        query.exactMatch(exactMatch);
        query.maxPrice(maxPrice);
        query.minPrice(minPrice);
        query.maxOrderSize(maxSize);
        query.minOrderSize(minSize);
        return query;
    }
}
