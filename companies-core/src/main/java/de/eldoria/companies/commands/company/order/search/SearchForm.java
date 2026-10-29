/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * The search form as the player filled it in, and the {@link SearchQuery} it stands for.
 * <p>
 * Materials are separated by spaces. A blank price or size means no limit. The option values are the ids of the form's
 * choices, such as {@link #ANY} or the lower case name of a {@link SortingType}.
 *
 * @param name           order name or part of it, blank for any
 * @param materials      materials, separated by spaces
 * @param materialSearch {@link #ANY} or {@link #ALL}
 * @param materialMatch  {@link #PART} or {@link #EXACT}
 * @param minPrice       lowest price
 * @param maxPrice       highest price
 * @param minSize        smallest number of items
 * @param maxSize        largest number of items
 * @param sorting        lower case name of the sorting
 * @param direction      {@link #ASC} or {@link #DESC}
 * @param fillable       whether only orders the player can deliver to are shown
 * @param serverOrders   {@link #ALL}, {@link #ONLY} or {@link #HIDE}: which orders of the server are shown, while the
 *                       server marks them; empty counts as {@link #ALL}
 * @param offers         {@link #ALL}, {@link #ONLY} for the chunks of event offers only, or the id of the one event
 *                       offer whose chunks are shown; empty counts as {@link #ALL}
 */
public record SearchForm(
        String name,
        String materials,
        String materialSearch,
        String materialMatch,
        String minPrice,
        String maxPrice,
        String minSize,
        String maxSize,
        String sorting,
        String direction,
        boolean fillable,
        String serverOrders,
        String offers) {
    public static final String NAME = "name";
    public static final String MATERIALS = "materials";
    public static final String MATERIAL_SEARCH = "material_search";
    public static final String MATERIAL_MATCH = "material_match";
    public static final String MIN_PRICE = "min_price";
    public static final String MAX_PRICE = "max_price";
    public static final String MIN_SIZE = "min_size";
    public static final String MAX_SIZE = "max_size";
    public static final String SORTING = "sorting";
    public static final String DIRECTION = "direction";
    public static final String FILLABLE = "fillable";
    public static final String SERVER_ORDERS = "server_orders";
    public static final String OFFERS = "offers";

    public static final String ANY = "any";
    public static final String ALL = "all";
    public static final String PART = "part";
    public static final String EXACT = "exact";
    public static final String ASC = "asc";
    public static final String DESC = "desc";
    public static final String ONLY = "only";
    public static final String HIDE = "hide";

    /**
     * The form filled in with a query.
     *
     * @param query query to show
     * @return the form
     */
    public static SearchForm of(SearchQuery query) {
        return new SearchForm(
                query.name(),
                String.join(" ", query.materials()),
                query.isAnyMaterial() ? ANY : ALL,
                query.isExactMatch() ? EXACT : PART,
                query.minPrice() > 0 ? plain(query.minPrice()) : "",
                query.maxPrice() == Double.MAX_VALUE ? "" : plain(query.maxPrice()),
                query.minOrderSize() > 0 ? String.valueOf(query.minOrderSize()) : "",
                query.maxOrderSize() == Integer.MAX_VALUE ? "" : String.valueOf(query.maxOrderSize()),
                sortingId(query.sortingType()),
                query.isAsc() ? ASC : DESC,
                query.fillable(),
                serverOrdersId(query.serverOrders()),
                offersId(query));
    }

    /**
     * The id of the event offer filter of a query in the form.
     *
     * @param query the query
     * @return the offer's id when it shows one offer, otherwise the lower case name of the choice
     */
    public static String offersId(SearchQuery query) {
        if (query.offer() > 0) return String.valueOf(query.offer());
        return query.eventOffers().name().toLowerCase(Locale.ROOT);
    }

    /**
     * The id of a choice of server orders in the form.
     *
     * @param serverOrders the choice
     * @return its lower case name
     */
    public static String serverOrdersId(SearchQuery.ServerOrders serverOrders) {
        return serverOrders.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The id of a sorting in the form.
     *
     * @param sorting sorting
     * @return its lower case name
     */
    public static String sortingId(SortingType sorting) {
        return sorting.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Reads the form into a fresh query.
     *
     * @return the query, or the key of the first input that holds no valid value
     */
    public Outcome toQuery() {
        var query = new SearchQuery();
        query.name(name.isBlank() ? null : name.trim());
        query.materials(new ArrayList<>(Arrays.stream(materials.trim().split("\\s+"))
                .filter(material -> !material.isBlank())
                .distinct()
                .toList()));
        if (!ANY.equals(materialSearch) && !ALL.equals(materialSearch)) return new Invalid(MATERIAL_SEARCH);
        query.anyMaterial(ANY.equals(materialSearch));
        if (!PART.equals(materialMatch) && !EXACT.equals(materialMatch)) return new Invalid(MATERIAL_MATCH);
        query.exactMatch(EXACT.equals(materialMatch));
        if (!ASC.equals(direction) && !DESC.equals(direction)) return new Invalid(DIRECTION);
        query.asc(ASC.equals(direction));
        var sortingType = Arrays.stream(SortingType.values())
                .filter(type -> sortingId(type).equals(sorting))
                .findFirst();
        if (sortingType.isEmpty()) return new Invalid(SORTING);
        query.sortingType(sortingType.get());
        query.fillable(fillable);
        var shown = serverOrders == null || serverOrders.isBlank() ? ALL : serverOrders;
        var choice = Arrays.stream(SearchQuery.ServerOrders.values())
                .filter(value -> serverOrdersId(value).equals(shown))
                .findFirst();
        if (choice.isEmpty()) return new Invalid(SERVER_ORDERS);
        query.serverOrders(choice.get());
        if (!offers(query)) return new Invalid(OFFERS);
        return ranges(query);
    }

    /**
     * @return false when the event offer filter is none of the choices and no offer id
     */
    private boolean offers(SearchQuery query) {
        var shown = offers == null || offers.isBlank() ? ALL : offers.trim();
        if (ALL.equals(shown)) {
            query.eventOffers(SearchQuery.EventOffers.ALL);
            return true;
        }
        if (ONLY.equals(shown)) {
            query.eventOffers(SearchQuery.EventOffers.ONLY);
            return true;
        }
        var id = size(shown, -1);
        if (id <= 0) return false;
        query.offer(id);
        return true;
    }

    private Outcome ranges(SearchQuery query) {
        var lowestPrice = price(minPrice, 0);
        if (lowestPrice < 0) return new Invalid(MIN_PRICE);
        var highestPrice = price(maxPrice, Double.MAX_VALUE);
        if (highestPrice < 0) return new Invalid(MAX_PRICE);
        var smallest = size(minSize, 0);
        if (smallest < 0) return new Invalid(MIN_SIZE);
        var largest = size(maxSize, Integer.MAX_VALUE);
        if (largest < 0) return new Invalid(MAX_SIZE);
        query.minPrice(lowestPrice);
        query.maxPrice(highestPrice);
        query.minOrderSize(smallest);
        query.maxOrderSize(largest);
        return new Valid(query);
    }

    /**
     * @return the price, the fallback when blank, or -1 when it is no non-negative finite number
     */
    private static double price(String input, double blank) {
        if (input.isBlank()) return blank;
        try {
            var value = Double.parseDouble(input.trim());
            return Double.isFinite(value) && value >= 0 ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * @return the size, the fallback when blank, or -1 when it is no non-negative whole number
     */
    private static int size(String input, int blank) {
        if (input.isBlank()) return blank;
        try {
            return Math.max(-1, Integer.parseInt(input.trim()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String plain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /**
     * What reading the form gave.
     */
    public sealed interface Outcome permits Valid, Invalid {}

    /**
     * The form holds a valid query.
     *
     * @param query the query
     */
    public record Valid(SearchQuery query) implements Outcome {}

    /**
     * An input of the form holds no valid value.
     *
     * @param input key of the input
     */
    public record Invalid(String input) implements Outcome {}
}
