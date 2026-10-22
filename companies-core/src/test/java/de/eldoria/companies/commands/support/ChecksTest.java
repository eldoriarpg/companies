/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.support;

import de.eldoria.companies.components.company.CompanyPermission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksTest {
    @Test
    void namesMustStayBelowTheLimit() {
        assertDoesNotThrow(() -> Checks.shorterThan("a".repeat(31), 32));
        var failure = assertThrows(CommandFailure.class, () -> Checks.shorterThan("a".repeat(32), 32));
        assertEquals("error.invalidLength", failure.localeKey());
    }

    @Test
    void failedConditionsCarryTheirKey() {
        assertDoesNotThrow(() -> Checks.isTrue(true, "error.x"));
        assertEquals(
                "error.x",
                assertThrows(CommandFailure.class, () -> Checks.isTrue(false, "error.x"))
                        .localeKey());
        assertEquals(
                "error.y",
                assertThrows(CommandFailure.class, () -> Checks.isFalse(true, "error.y"))
                        .localeKey());
        assertFalse(CommandFailure.of("error.x").isActionBar());
        assertTrue(CommandFailure.actionBar("error.x").isActionBar());
    }

    @Test
    void numbersAreParsed() {
        assertEquals(12, Checks.integer(" 12 "));
        assertEquals(2.5, Checks.finiteDouble("2.5"));
        assertEquals(
                "error.invalidNumber",
                assertThrows(CommandFailure.class, () -> Checks.integer("twelve"))
                        .localeKey());
        assertThrows(CommandFailure.class, () -> Checks.finiteDouble("NaN"));
        assertThrows(CommandFailure.class, () -> Checks.finiteDouble("Infinity"));
    }

    @Test
    void enumValuesIgnoreCase() {
        assertEquals(CompanyPermission.MANAGE_ORDERS, Checks.enumValue("manage_orders", CompanyPermission.class));
        assertEquals(CompanyPermission.MANAGE_ORDERS, Checks.enumValue("MANAGE_ORDERS", CompanyPermission.class));
        var failure = assertThrows(CommandFailure.class, () -> Checks.enumValue("everything", CompanyPermission.class));
        assertEquals("error.invalidEnumValue", failure.localeKey());
    }
}
