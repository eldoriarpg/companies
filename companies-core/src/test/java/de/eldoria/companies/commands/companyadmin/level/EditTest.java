/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.commands.support.CommandFailure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EditTest {
    @Test
    void noneClearsTheTax() {
        assertNull(Edit.taxPercent("none"));
        assertNull(Edit.taxPercent("NONE"));
    }

    @Test
    void taxIsClampedToAPercentage() {
        assertEquals(5.5, Edit.taxPercent("5.5"));
        assertEquals(0.0, Edit.taxPercent("-3"));
        assertEquals(100.0, Edit.taxPercent("250"));
    }

    @Test
    void otherTaxInputIsRejected() {
        assertThrows(CommandFailure.class, () -> Edit.taxPercent("lots"));
        assertThrows(CommandFailure.class, () -> Edit.taxPercent("NaN"));
    }
}
