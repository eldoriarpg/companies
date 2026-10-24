/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Which orders a search shows: the filter part of a search without its sorting.
 * <p>
 * The order search matches it in SQL; {@link #matches(FullOrder)} matches one order in Java with the same rules, so
 * saved filters such as alerts can be checked against a single new order. It is stored as JSON; unknown fields are
 * ignored on read, so the record can grow.
 *
 * @param name        part of the order name, ignoring case, or null for any name
 * @param materials   materials of which the order must contain one or all, ignoring case; empty for any
 * @param anyMaterial true when one of the materials is enough, false when every material is required
 * @param exactMatch  true when a material must equal a line's material, false when the line's material only has to
 *                    contain it
 * @param minPrice    lowest total price, inclusive
 * @param maxPrice    highest total price, inclusive
 * @param minSize     fewest items, inclusive
 * @param maxSize     most items, inclusive
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderFilter(
        @Nullable String name,
        List<String> materials,
        boolean anyMaterial,
        boolean exactMatch,
        double minPrice,
        double maxPrice,
        int minSize,
        int maxSize) {
    /**
     * The longest name part a filter keeps.
     */
    public static final int MAX_NAME_LENGTH = 32;

    /**
     * The filter that matches every order.
     */
    public static final OrderFilter ALL =
            new OrderFilter(null, List.of(), true, false, 0.0, Double.MAX_VALUE, 0, Integer.MAX_VALUE);

    /**
     * Creates a filter. A blank name counts as no name and longer names are cut to {@link #MAX_NAME_LENGTH}.
     */
    public OrderFilter {
        name = name == null || name.isBlank() ? null : name.substring(0, Math.min(name.length(), MAX_NAME_LENGTH));
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    /**
     * Whether an order passes this filter, with the semantics of the SQL search: the name contains the name part
     * ignoring case, the materials match per line, and price and item count lie inside the inclusive ranges. An order
     * without lines never matches, as it has no price in the search.
     *
     * @param order order to check
     * @return true when the order passes
     */
    public boolean matches(FullOrder order) {
        if (order.contents().isEmpty()) return false;
        if (name != null && !lower(order.name()).contains(lower(name))) return false;
        var price = order.price();
        if (price < minPrice || price > maxPrice) return false;
        var amount = order.amount();
        if (amount < minSize || amount > maxSize) return false;
        return matchesMaterials(order.contents());
    }

    private boolean matchesMaterials(List<OrderContent> contents) {
        if (materials.isEmpty()) return true;
        var lines = contents.stream().map(OrderContent::materialIdentifier).toList();
        if (anyMaterial) return materials.stream().anyMatch(material -> matchedByLine(lines, material));
        return materials.stream().allMatch(material -> matchedByLine(lines, material));
    }

    private boolean matchedByLine(List<String> lines, String material) {
        var wanted = lower(material);
        return lines.stream()
                .map(OrderFilter::lower)
                .anyMatch(line -> exactMatch ? line.equals(wanted) : line.contains(wanted));
    }

    /**
     * A copy with another name part.
     *
     * @param name part of the order name, or null for any name
     * @return changed filter
     */
    public OrderFilter withName(@Nullable String name) {
        return new OrderFilter(name, materials, anyMaterial, exactMatch, minPrice, maxPrice, minSize, maxSize);
    }

    /**
     * A copy with other materials.
     *
     * @param materials materials, empty for any
     * @return changed filter
     */
    public OrderFilter withMaterials(List<String> materials) {
        return new OrderFilter(name, materials, anyMaterial, exactMatch, minPrice, maxPrice, minSize, maxSize);
    }

    /**
     * A copy with another material mode.
     *
     * @param anyMaterial true when one material is enough
     * @param exactMatch  true when materials must match exactly
     * @return changed filter
     */
    public OrderFilter withMaterialMode(boolean anyMaterial, boolean exactMatch) {
        return new OrderFilter(name, materials, anyMaterial, exactMatch, minPrice, maxPrice, minSize, maxSize);
    }

    /**
     * A copy with another price range. Negative bounds count as 0.
     *
     * @param minPrice lowest price
     * @param maxPrice highest price
     * @return changed filter
     */
    public OrderFilter withPrice(double minPrice, double maxPrice) {
        return new OrderFilter(
                name,
                materials,
                anyMaterial,
                exactMatch,
                Math.max(0, minPrice),
                Math.max(0, maxPrice),
                minSize,
                maxSize);
    }

    /**
     * A copy with another item count range. Negative bounds count as 0.
     *
     * @param minSize fewest items
     * @param maxSize most items
     * @return changed filter
     */
    public OrderFilter withSize(int minSize, int maxSize) {
        return new OrderFilter(
                name,
                materials,
                anyMaterial,
                exactMatch,
                minPrice,
                maxPrice,
                Math.max(0, minSize),
                Math.max(0, maxSize));
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
