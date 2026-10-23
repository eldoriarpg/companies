/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelFieldTest {
    @Test
    void fieldsAreFoundByTheirCommandName() {
        assertEquals(Optional.of(LevelField.TAX_PERCENT), LevelField.byKey("tax_percent"));
        assertEquals(Optional.of(LevelField.MAX_ORDER_SIZE), LevelField.byKey("MAX_ORDER_SIZE"));
        assertTrue(LevelField.byKey("level").isEmpty());
    }

    @Test
    void noneOrNothingClearsTheTax() {
        assertNull(LevelField.taxPercent("none"));
        assertNull(LevelField.taxPercent("NONE"));
        assertNull(LevelField.taxPercent(" "));
    }

    @Test
    void taxIsClampedToAPercentage() {
        assertEquals(5.5, LevelField.taxPercent("5.5"));
        assertEquals(0.0, LevelField.taxPercent("-3"));
        assertEquals(100.0, LevelField.taxPercent("250"));
    }

    @Test
    void noneOrNothingClearsTheAlertLimit() {
        assertNull(LevelField.maxAlerts("none"));
        assertNull(LevelField.maxAlerts(""));
    }

    @Test
    void alertLimitIsAWholeNumberOfAtLeastZero() {
        assertEquals(3, LevelField.maxAlerts("3"));
        assertEquals(0, LevelField.maxAlerts("-2"));
        assertThrows(CommandFailure.class, () -> LevelField.maxAlerts("1.5"));
    }

    @Test
    void otherTaxInputIsRejected() {
        assertThrows(CommandFailure.class, () -> LevelField.taxPercent("lots"));
        assertThrows(CommandFailure.class, () -> LevelField.taxPercent("NaN"));
    }

    @Test
    void everyFieldIsAppliedToTheLevel() {
        var level = new CompanyLevel();

        LevelField.NAME.apply(level, "<red>Guild");
        LevelField.ORDER_COUNT.apply(level, "3");
        LevelField.MEMBER_COUNT.apply(level, "4");
        LevelField.EARNED_MONEY.apply(level, "250.5");
        LevelField.DELIVERED_ITEMS.apply(level, "600");
        LevelField.MAX_MEMBERS.apply(level, "8");
        LevelField.MAX_ORDERS.apply(level, "9");
        LevelField.MAX_ORDER_SIZE.apply(level, "-5");
        LevelField.DELIVERY_HOURS_BONUS.apply(level, "12");
        LevelField.TAX_PERCENT.apply(level, "2.5");
        LevelField.MAX_ALERTS.apply(level, "4");

        assertEquals("<red>Guild", level.levelName());
        assertEquals(3, level.requirement().orderCount());
        assertEquals(4, level.requirement().memberCount());
        assertEquals(250.5, level.requirement().earnedMoney());
        assertEquals(600, level.requirement().deliveredItems());
        assertEquals(8, level.settings().maxMembers());
        assertEquals(9, level.settings().maxOrders());
        assertEquals(0, level.settings().maxOrderSize());
        assertEquals(12, level.settings().deliveryHoursBonus());
        assertEquals(Optional.of(2.5), level.settings().taxPercent());
        assertEquals(Optional.of(4), level.settings().maxAlerts());
    }

    @Test
    void parsingChangesNothingUntilApplied() {
        var level = new CompanyLevel();

        var change = LevelField.MAX_ORDERS.parse("42");

        assertEquals(5, level.settings().maxOrders());
        change.accept(level);
        assertEquals(42, level.settings().maxOrders());
    }

    @Test
    void invalidNumbersAreRejected() {
        var level = new CompanyLevel();

        assertThrows(CommandFailure.class, () -> LevelField.ORDER_COUNT.apply(level, "many"));
        assertThrows(CommandFailure.class, () -> LevelField.EARNED_MONEY.apply(level, "Infinity"));
    }

    @Test
    void inputsReadBackAsTheyAreEntered() {
        var level = new CompanyLevel();
        LevelField.EARNED_MONEY.apply(level, "1500");

        for (var field : LevelField.values()) {
            var copy = new CompanyLevel();
            field.apply(copy, field.input(level));
            assertEquals(field.input(level), field.input(copy), field.key());
        }
        assertEquals("1500", LevelField.EARNED_MONEY.input(level));
        assertEquals("", LevelField.TAX_PERCENT.input(level));
        assertEquals("", LevelField.MAX_ALERTS.input(level));
    }

    @Test
    void settingsThatChangeNothingAreNotShown() {
        var level = new CompanyLevel();

        assertTrue(LevelField.MAX_ORDER_SIZE.shown(level).isEmpty());
        assertTrue(LevelField.DELIVERY_HOURS_BONUS.shown(level).isEmpty());
        assertTrue(LevelField.TAX_PERCENT.shown(level).isEmpty());
        assertTrue(LevelField.MAX_ALERTS.shown(level).isEmpty());

        LevelField.MAX_ORDER_SIZE.apply(level, "64");
        LevelField.DELIVERY_HOURS_BONUS.apply(level, "6");
        LevelField.TAX_PERCENT.apply(level, "5");
        LevelField.MAX_ALERTS.apply(level, "0");

        assertEquals(Optional.of("64"), LevelField.MAX_ORDER_SIZE.shown(level));
        assertEquals(Optional.of("+6 h"), LevelField.DELIVERY_HOURS_BONUS.shown(level));
        assertEquals(Optional.of("5 %"), LevelField.TAX_PERCENT.shown(level));
        assertEquals(Optional.of("0"), LevelField.MAX_ALERTS.shown(level));
    }
}
