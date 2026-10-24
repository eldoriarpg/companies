/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelFormTest {
    @Test
    void theFormReadsEveryFieldItHolds() {
        Map<String, String> inputs = Map.of("name", "Guild", "max_orders", "7", "unrelated", "x");

        var entered = LevelForm.read(inputs::get);

        assertEquals(Map.of(LevelField.NAME, "Guild", LevelField.MAX_ORDERS, "7"), entered);
    }

    @Test
    void thePositionIsReadWhenChosen() {
        assertEquals(OptionalInt.of(3), LevelForm.position(Map.of(LevelForm.POSITION, "3")::get));
        assertEquals(OptionalInt.empty(), LevelForm.position(Map.<String, String>of()::get));
        assertEquals(OptionalInt.empty(), LevelForm.position(Map.of(LevelForm.POSITION, "top")::get));
    }

    @Test
    void validValuesAreAppliedTogether() {
        var level = new CompanyLevel();
        Map<LevelField, String> entered = new EnumMap<>(LevelField.class);
        entered.put(LevelField.NAME, "Guild");
        entered.put(LevelField.MAX_ORDERS, "7");
        entered.put(LevelField.TAX_PERCENT, "");

        var valid = assertInstanceOf(LevelForm.Valid.class, LevelForm.parse(entered));
        valid.changes().accept(level);

        assertEquals("Guild", level.levelName());
        assertEquals(7, level.settings().maxOrders());
        assertTrue(level.settings().taxPercent().isEmpty());
    }

    @Test
    void anInvalidValueNamesItsFieldAndChangesNothing() {
        var level = new CompanyLevel();
        Map<LevelField, String> entered = new EnumMap<>(LevelField.class);
        entered.put(LevelField.NAME, "Guild");
        entered.put(LevelField.MAX_MEMBERS, "lots");

        var invalid = assertInstanceOf(LevelForm.Invalid.class, LevelForm.parse(entered));

        assertEquals(LevelField.MAX_MEMBERS, invalid.field());
        assertEquals("error.invalidNumber", invalid.failure().localeKey());
        assertEquals("none", level.levelName());
    }

    @Test
    void theEditorStartsWithTheCurrentValues() {
        var settings = new CompanySettings();
        var level = settings.level().get(1);

        var view = LevelView.of(level, settings.level().size());

        for (var field : LevelField.values()) {
            assertEquals(field.input(level), view.entered(field), field.key());
        }
        assertTrue(view.rejection().isEmpty());
    }

    @Test
    void aRefusedEntryIsShownAgain() {
        var settings = new CompanySettings();
        var view = LevelView.of(settings.level().get(0), settings.level().size());
        var entered = Map.of(LevelField.MAX_MEMBERS, "lots");
        var invalid = assertInstanceOf(LevelForm.Invalid.class, LevelForm.parse(entered));

        var shown = view.rejected(entered, new LevelView.Rejection(Optional.of(invalid.field()), invalid.failure()));

        assertEquals("lots", shown.entered(LevelField.MAX_MEMBERS));
        assertEquals("", shown.entered(LevelField.NAME));
        assertEquals(
                LevelField.MAX_MEMBERS, shown.rejection().orElseThrow().field().orElseThrow());
    }

    @Test
    void theListOffersEveryPositionForANewLevel() {
        var settings = new CompanySettings();

        var view = LevelListView.of(settings);

        assertEquals(settings.level(), view.levels());
        assertEquals(settings.level().size() + 1, view.lastPosition());
    }
}
