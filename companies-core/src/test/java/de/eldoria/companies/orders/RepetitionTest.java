/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.ordersettings.RepeatingSettings;
import de.eldoria.companies.data.wrapper.series.RepeatInterval;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepetitionTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    private final RepeatingSettings settings = new RepeatingSettings();

    @Test
    void aCountWithinTheLimitIsAccepted() {
        assertDoesNotThrow(() -> Repetition.times(RepeatInterval.WEEKLY, 52).validate(settings, TODAY));
        assertDoesNotThrow(() -> Repetition.times(RepeatInterval.DAILY, 1).validate(settings, TODAY));
    }

    @Test
    void aCountAboveTheLimitIsRefused() {
        settings.maxCopies(4);

        assertEquals("order.draft.repeat.error.tooMany", refusal(Repetition.times(RepeatInterval.DAILY, 5)));
    }

    @Test
    void aCountBelowOneIsRefused() {
        assertEquals("error.invalidNumber", refusal(Repetition.times(RepeatInterval.DAILY, 0)));
    }

    @Test
    void aDateWithinTheLimitIsAccepted() {
        settings.maxDays(30);

        assertDoesNotThrow(
                () -> Repetition.until(RepeatInterval.DAILY, TODAY.plusDays(1)).validate(settings, TODAY));
        assertDoesNotThrow(
                () -> Repetition.until(RepeatInterval.DAILY, TODAY.plusDays(30)).validate(settings, TODAY));
    }

    @Test
    void aDateTooFarAheadIsRefused() {
        settings.maxDays(30);

        assertEquals(
                "order.draft.repeat.error.tooLong",
                refusal(Repetition.until(RepeatInterval.DAILY, TODAY.plusDays(31))));
    }

    @Test
    void todayOrAPastDateIsRefused() {
        assertEquals("order.draft.repeat.error.past", refusal(Repetition.until(RepeatInterval.WEEKLY, TODAY)));
        assertEquals(
                "order.draft.repeat.error.past", refusal(Repetition.until(RepeatInterval.WEEKLY, TODAY.minusDays(1))));
    }

    @Test
    void anEndIsRequired() {
        var endless = new Repetition(RepeatInterval.DAILY, OptionalInt.empty(), Optional.empty());

        assertEquals("order.draft.repeat.error.noEnd", refusal(endless));
    }

    @Test
    void nothingRepeatsWhileDisabled() {
        settings.enabled(false);

        assertEquals("order.draft.repeat.error.disabled", refusal(Repetition.times(RepeatInterval.DAILY, 2)));
    }

    @Test
    void anEndDateIncludesItsWholeDay() {
        var repetition = Repetition.until(RepeatInterval.DAILY, TODAY);

        assertEquals(Optional.of(LocalDateTime.of(2026, 6, 1, 23, 59, 59)), repetition.ends());
        assertEquals(Optional.empty(), Repetition.times(RepeatInterval.DAILY, 3).ends());
    }

    @Test
    void typedValuesAreRead() {
        assertEquals(Optional.of(RepeatInterval.WEEKLY), Repetition.interval(" Weekly "));
        assertEquals(Optional.empty(), Repetition.interval("once"));
        assertEquals(Optional.of(LocalDate.of(2026, 12, 24)), Repetition.date("2026-12-24"));
        assertEquals(Optional.empty(), Repetition.date("24.12.2026"));
        assertEquals(OptionalInt.of(4), Repetition.count(" 4 "));
        assertEquals(OptionalInt.empty(), Repetition.count("four"));
        assertEquals(OptionalInt.empty(), Repetition.count(""));
    }

    private String refusal(Repetition repetition) {
        return assertThrows(CommandFailure.class, () -> repetition.validate(settings, TODAY))
                .localeKey();
    }
}
