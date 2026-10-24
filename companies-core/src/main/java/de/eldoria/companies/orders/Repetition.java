/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.ordersettings.RepeatingSettings;
import de.eldoria.companies.data.wrapper.series.RepeatInterval;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.messages.Replacement;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * How a draft repeats once it is finished: every day or every week, until a number of copies was posted or until a
 * date. At least one end is required.
 * <p>
 * An end date includes its whole day.
 *
 * @param interval how often a copy is posted
 * @param count    how many copies are posted at most, or empty
 * @param until    the last day a copy may be posted, or empty
 */
public record Repetition(RepeatInterval interval, OptionalInt count, Optional<LocalDate> until) {
    /**
     * A repetition ending after a number of copies.
     *
     * @param interval how often a copy is posted
     * @param count    how many copies are posted
     * @return the repetition
     */
    public static Repetition times(RepeatInterval interval, int count) {
        return new Repetition(interval, OptionalInt.of(count), Optional.empty());
    }

    /**
     * A repetition ending at a date.
     *
     * @param interval how often a copy is posted
     * @param until    the last day a copy may be posted
     * @return the repetition
     */
    public static Repetition until(RepeatInterval interval, LocalDate until) {
        return new Repetition(interval, OptionalInt.empty(), Optional.of(until));
    }

    /**
     * Reads an interval as typed or chosen, such as {@code daily} or {@code WEEKLY}.
     *
     * @param text entered text
     * @return the interval, empty when there is none of that name
     */
    public static Optional<RepeatInterval> interval(@Nullable String text) {
        if (text == null) return Optional.empty();
        var name = text.strip().toUpperCase(Locale.ROOT);
        for (var interval : RepeatInterval.values()) {
            if (interval.name().equals(name)) return Optional.of(interval);
        }
        return Optional.empty();
    }

    /**
     * Reads an end date written as {@code yyyy-MM-dd}.
     *
     * @param text entered text
     * @return the date, empty when the text is no such date
     */
    public static Optional<LocalDate> date(@Nullable String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(text.strip()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads a number of copies.
     *
     * @param text entered text
     * @return the count, empty when the text is no whole number
     */
    public static OptionalInt count(@Nullable String text) {
        if (text == null || text.isBlank()) return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseInt(text.strip()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /**
     * Checks the repetition against the settings.
     *
     * @param settings repeating order settings
     * @param today    current date
     * @throws CommandFailure when repeating orders are disabled, no end is given, the count is outside 1 and
     *                        {@link RepeatingSettings#maxCopies()} or the date is not between tomorrow and
     *                        {@link RepeatingSettings#maxDays()} days from today
     */
    public void validate(RepeatingSettings settings, LocalDate today) {
        if (!settings.enabled()) throw CommandFailure.actionBar("order.draft.repeat.error.disabled");
        if (count.isEmpty() && until.isEmpty()) throw CommandFailure.actionBar("order.draft.repeat.error.noEnd");
        if (count.isPresent()) {
            if (count.getAsInt() < 1) throw CommandFailure.actionBar("error.invalidNumber");
            if (count.getAsInt() > settings.maxCopies()) {
                throw CommandFailure.actionBar(
                        "order.draft.repeat.error.tooMany",
                        Replacement.create("max", String.valueOf(settings.maxCopies())));
            }
        }
        if (until.isPresent()) {
            if (!until.get().isAfter(today)) throw CommandFailure.actionBar("order.draft.repeat.error.past");
            if (until.get().isAfter(today.plusDays(settings.maxDays()))) {
                throw CommandFailure.actionBar(
                        "order.draft.repeat.error.tooLong",
                        Replacement.create("max", String.valueOf(settings.maxDays())));
            }
        }
    }

    /**
     * The point in time after which no copy is posted: the end of the end date in the server's zone, as a stored time.
     *
     * @return the end, or empty without end date
     */
    public Optional<LocalDateTime> ends() {
        return until.map(date -> Times.startOfDay(date.plusDays(1)).minusSeconds(1));
    }
}
