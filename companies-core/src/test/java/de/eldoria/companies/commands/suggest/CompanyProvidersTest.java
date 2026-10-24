/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyProvidersTest {
    @Test
    void matchingKeepsValuesStartingWithTheInputIgnoringCase() {
        assertEquals(List.of("Alex", "alice"), CompanyProviders.matching(List.of("Alex", "Steve", "alice"), "AL"));
        assertEquals(List.of("Alex", "Steve"), CompanyProviders.matching(List.of("Alex", "Steve"), ""));
    }

    @Test
    void singleWordsStayUnquoted() {
        assertEquals("Miners", CompanyProviders.quoted("Miners"));
        assertEquals("iron-works_2.0", CompanyProviders.quoted("iron-works_2.0"));
    }

    @Test
    void otherNamesAreQuotedAndEscaped() {
        assertEquals("\"Iron Works\"", CompanyProviders.quoted("Iron Works"));
        assertEquals("\"Say \\\"hi\\\"\"", CompanyProviders.quoted("Say \"hi\""));
        assertEquals("\"back\\\\slash\"", CompanyProviders.quoted("back\\slash"));
        assertEquals("\"\"", CompanyProviders.quoted(""));
    }

    @Test
    void theOwnerPermissionIsNotOffered() {
        var names = CompanyProviders.permissionNames();
        assertFalse(names.contains("owner"));
        assertTrue(names.contains("manage_orders"));
        assertTrue(names.stream().allMatch(name -> name.equals(name.toLowerCase())));
    }
}
