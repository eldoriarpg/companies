/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchFormTest {
    private static SearchForm form(String minPrice, String maxPrice, String minSize, String maxSize) {
        return new SearchForm("", "", "any", "part", minPrice, maxPrice, minSize, maxSize, "age", "asc", false, "");
    }

    private static SearchQuery valid(SearchForm form) {
        return assertInstanceOf(SearchForm.Valid.class, form.toQuery()).query();
    }

    private static String invalid(SearchForm form) {
        return assertInstanceOf(SearchForm.Invalid.class, form.toQuery()).input();
    }

    @Test
    void anUnknownChoiceOfServerOrdersIsInvalid() {
        var form = new SearchForm("", "", "any", "part", "", "", "", "", "age", "asc", false, "some");

        assertEquals(SearchForm.SERVER_ORDERS, invalid(form));
    }

    @Test
    void everyInputReachesTheQuery() {
        var query = valid(new SearchForm(
                " Wood ",
                "oak_log  birch_log oak_log",
                "all",
                "exact",
                "1.5",
                "20",
                "3",
                "64",
                "unit_price",
                "desc",
                true,
                "hide"));

        assertEquals("Wood", query.name());
        assertEquals(SearchQuery.ServerOrders.HIDE, query.serverOrders());
        assertEquals(List.of("oak_log", "birch_log"), query.materials());
        assertFalse(query.isAnyMaterial());
        assertTrue(query.isExactMatch());
        assertEquals(1.5, query.minPrice());
        assertEquals(20, query.maxPrice());
        assertEquals(3, query.minOrderSize());
        assertEquals(64, query.maxOrderSize());
        assertEquals(SortingType.UNIT_PRICE, query.sortingType());
        assertFalse(query.isAsc());
        assertTrue(query.fillable());
    }

    @Test
    void blankInputsMeanNoLimit() {
        var query = valid(form("", " ", "", ""));

        assertEquals("", query.name());
        assertEquals(List.of(), query.materials());
        assertEquals(0, query.minPrice());
        assertEquals(Double.MAX_VALUE, query.maxPrice());
        assertEquals(0, query.minOrderSize());
        assertEquals(Integer.MAX_VALUE, query.maxOrderSize());
    }

    @Test
    void invalidNumbersNameTheirInput() {
        assertEquals(SearchForm.MIN_PRICE, invalid(form("cheap", "", "", "")));
        assertEquals(SearchForm.MAX_PRICE, invalid(form("", "-1", "", "")));
        assertEquals(SearchForm.MAX_PRICE, invalid(form("", "NaN", "", "")));
        assertEquals(SearchForm.MIN_SIZE, invalid(form("", "", "1.5", "")));
        assertEquals(SearchForm.MAX_SIZE, invalid(form("", "", "", "-3")));
    }

    @Test
    void unknownOptionsNameTheirInput() {
        var form = new SearchForm("", "", "any", "part", "", "", "", "", "colour", "asc", false, "");

        assertEquals(SearchForm.SORTING, invalid(form));
        assertEquals(
                SearchForm.MATERIAL_SEARCH,
                invalid(new SearchForm("", "", "some", "part", "", "", "", "", "age", "asc", false, "")));
    }

    @Test
    void aFormOfAQueryGivesTheSameQuery() {
        var query = new SearchQuery();
        query.name("Stone");
        query.materials(List.of("STONE", "DIRT"));
        query.anyMaterial(false);
        query.exactMatch(true);
        query.minPrice(2.5);
        query.maxPrice(100);
        query.minOrderSize(10);
        query.sortingType(SortingType.PRICE);
        query.asc(false);
        query.fillable(true);

        var form = SearchForm.of(query);
        var again = valid(form);

        assertEquals("2.5", form.minPrice());
        assertEquals("", form.maxSize());
        assertEquals("price", form.sorting());
        assertEquals(form, SearchForm.of(again));
    }

    @Test
    void aNewQueryGivesAnEmptyForm() {
        var form = SearchForm.of(new SearchQuery());

        assertEquals(new SearchForm("", "", "any", "part", "", "", "", "", "age", "asc", false, "all"), form);
    }
}
