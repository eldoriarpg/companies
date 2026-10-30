/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.menus.admin.EventForm.Field;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventFormTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Test
    void aNewFormStartsNowAndRunsADay() {
        var event = valid(
                EventForm.of("Tax holiday").with(Field.KIND, "tax_reduction").with(Field.VALUE, "5"));

        assertEquals(EventKind.TAX_REDUCTION, event.event().kind());
        assertEquals(NOW, event.event().starts());
        assertEquals(NOW.plusHours(24), event.event().ends());
        assertNull(event.event().funding());
        assertNull(event.event().account());
        assertEquals("Nora", event.event().createdBy());
    }

    @Test
    void aMultiplierIsPaidByTheCollectorUnlessTold() {
        var form = EventForm.of("Harvest")
                .with(Field.KIND, "REWARD_MULTIPLIER")
                .with(Field.VALUE, "1,5")
                .with(Field.MATERIALS, "wheat, carrot potato")
                .with(Field.LEVELS, "2 3")
                .with(Field.START, "2026-12-24 18:00")
                .with(Field.END, "1d12h");

        var account = valid(form).event();
        var minted = valid(form.with(Field.FUNDING, "minted").with(Field.BUDGET, "500"))
                .event();

        assertEquals(1.5, account.value());
        assertEquals(List.of("wheat", "carrot", "potato"), account.materials());
        assertEquals(List.of(2, 3), account.levels());
        assertEquals(LocalDateTime.of(2026, 12, 24, 17, 0), account.starts());
        assertEquals(LocalDateTime.of(2026, 12, 26, 5, 0), account.ends());
        assertEquals(SourceType.ACCOUNT, account.funding());
        assertEquals("Collector", account.account());
        assertEquals(SourceType.MINTED, minted.funding());
        assertNull(minted.account());
        assertEquals(500.0, minted.budget());
    }

    @Test
    void theFirstInvalidFieldIsNamed() {
        var base = EventForm.of("Harvest").with(Field.KIND, "REWARD_MULTIPLIER").with(Field.VALUE, "2");

        assertEquals(Field.KIND, invalid(EventForm.of("x").with(Field.KIND, "BOOST")));
        assertEquals(Field.VALUE, invalid(base.with(Field.VALUE, "lots")));
        assertEquals(Field.LEVELS, invalid(base.with(Field.LEVELS, "one")));
        assertEquals(Field.START, invalid(base.with(Field.START, "tomorrow")));
        assertEquals(Field.END, invalid(base.with(Field.END, "forever")));
        assertEquals(Field.FUNDING, invalid(base.with(Field.FUNDING, "bank")));
        assertEquals(Field.BUDGET, invalid(base.with(Field.BUDGET, "-5")));
        assertEquals(
                Field.ACCOUNT,
                assertInstanceOf(EventForm.Invalid.class, base.parse(BERLIN, NOW, "", "Nora"))
                        .field());
    }

    @Test
    void anEmptiedFieldIsLeftOut() {
        var form = EventForm.of("x").with(Field.MATERIALS, "wheat").with(Field.MATERIALS, " ");

        assertEquals("", form.text(Field.MATERIALS));
        assertEquals(Field.KIND, EventForm.Field.of("KIND").orElseThrow());
    }

    private static EventForm.Valid valid(EventForm form) {
        return assertInstanceOf(EventForm.Valid.class, form.parse(BERLIN, NOW, "Collector", "Nora"));
    }

    private static Field invalid(EventForm form) {
        return assertInstanceOf(EventForm.Invalid.class, form.parse(BERLIN, NOW, "Collector", "Nora"))
                .field();
    }
}
