/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.services.events.EventTimes;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The form creating a server event: its name and the text an admin entered for each {@link Field}, read as a whole
 * when the event is created, so a form with one invalid value creates nothing.
 * <p>
 * The start is {@code now}, a duration from now such as {@code 2h}, or a date such as {@code 2026-12-24 18:00}; the end
 * a duration from the start such as {@code 48h}, or a date. Dates are in the server's time zone. Materials and levels
 * are separated by commas or spaces, empty for every material or company. A reward multiplier is funded from an
 * account unless {@code MINTED} is entered; without an account, the collector account of {@code economy} pays.
 *
 * @param name    name of the event
 * @param entered the text of each field, missing for fields left empty
 */
public record EventForm(String name, Map<Field, String> entered) {
    /**
     * @param name    name of the event
     * @param entered the text of each field
     */
    public EventForm {
        var copy = new EnumMap<Field, String>(Field.class);
        copy.putAll(entered);
        entered = Collections.unmodifiableMap(copy);
    }

    /**
     * A new form with the defaults: starting now and running a day.
     *
     * @param name name of the event
     * @return the form
     */
    public static EventForm of(String name) {
        return new EventForm(name, Map.of(Field.START, "now", Field.END, "24h"));
    }

    /**
     * @param field a field
     * @param text  its new text, blank to empty it
     * @return a copy with the field changed
     */
    public EventForm with(Field field, String text) {
        var changed = new EnumMap<Field, String>(Field.class);
        changed.putAll(entered);
        if (text.isBlank()) changed.remove(field);
        else changed.put(field, text.strip());
        return new EventForm(name, changed);
    }

    /**
     * @param field a field
     * @return its text, empty when it was left empty
     */
    public String text(Field field) {
        return entered.getOrDefault(field, "");
    }

    /**
     * Reads every field.
     *
     * @param zone      the server's time zone
     * @param now       the current time in UTC
     * @param collector the collector account, paying multipliers funded from an account that name none
     * @param createdBy who creates the event
     * @return the event, or the first field holding an invalid value
     */
    public Result parse(ZoneId zone, LocalDateTime now, String collector, String createdBy) {
        var kind = parseKind(text(Field.KIND));
        if (kind == null) return new Invalid(Field.KIND);
        var value = number(text(Field.VALUE));
        if (value == null) return new Invalid(Field.VALUE);
        var materials = words(text(Field.MATERIALS));
        List<Integer> levels = new ArrayList<>();
        for (var level : words(text(Field.LEVELS))) {
            try {
                levels.add(Integer.parseInt(level));
            } catch (NumberFormatException e) {
                return new Invalid(Field.LEVELS);
            }
        }
        var starts = start(text(Field.START), zone, now);
        if (starts.isEmpty()) return new Invalid(Field.START);
        var ends = end(text(Field.END), zone, starts.get());
        if (ends.isEmpty()) return new Invalid(Field.END);
        MoneySource.SourceType funding = null;
        String account = null;
        Double budget = null;
        if (kind.funded()) {
            var source = text(Field.FUNDING).isBlank()
                    ? "ACCOUNT"
                    : text(Field.FUNDING).toUpperCase(Locale.ROOT);
            try {
                funding = MoneySource.SourceType.valueOf(source);
            } catch (IllegalArgumentException e) {
                return new Invalid(Field.FUNDING);
            }
            if (funding == MoneySource.SourceType.ACCOUNT) {
                account = text(Field.ACCOUNT).isBlank() ? collector : text(Field.ACCOUNT);
                if (account.isBlank()) return new Invalid(Field.ACCOUNT);
            }
            if (!text(Field.BUDGET).isBlank()) {
                budget = number(text(Field.BUDGET));
                if (budget == null || !(budget > 0)) return new Invalid(Field.BUDGET);
            }
        }
        return new Valid(ServerEvent.draft(
                name.strip(),
                kind,
                value,
                materials,
                levels,
                starts.get(),
                ends.get(),
                funding,
                account,
                budget,
                null,
                createdBy,
                now));
    }

    private static @Nullable EventKind parseKind(String text) {
        try {
            return EventKind.valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @param text a number, with a point or a comma
     * @return the number, null when the text is none
     */
    public static @Nullable Double number(String text) {
        try {
            var value = Double.parseDouble(text.strip().replace(',', '.'));
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> words(String text) {
        return Arrays.stream(text.split("[,\\s]+"))
                .filter(word -> !word.isBlank())
                .toList();
    }

    private static Optional<LocalDateTime> start(String text, ZoneId zone, LocalDateTime now) {
        if (text.isBlank() || text.strip().equalsIgnoreCase("now")) return Optional.of(now);
        var after = EventTimes.duration(text);
        if (after.isPresent()) return Optional.of(now.plus(after.get()));
        return EventTimes.date(text, zone);
    }

    private static Optional<LocalDateTime> end(String text, ZoneId zone, LocalDateTime start) {
        var after = EventTimes.duration(text);
        if (after.isPresent()) return Optional.of(start.plus(after.get()));
        return EventTimes.date(text, zone);
    }

    /**
     * A field of the form.
     */
    public enum Field {
        /** What the event changes: {@code TAX_REDUCTION}, {@code REWARD_MULTIPLIER} or {@code FEE_REDUCTION}. */
        KIND("kind"),
        /** How much it changes. */
        VALUE("value"),
        /** The materials it is limited to. */
        MATERIALS("materials"),
        /** The company levels it is limited to. */
        LEVELS("levels"),
        /** When it starts. */
        START("start"),
        /** When it ends. */
        END("end"),
        /** Where the money of a multiplier comes from: {@code ACCOUNT} or {@code MINTED}. */
        FUNDING("funding"),
        /** The account paying a multiplier. */
        ACCOUNT("account"),
        /** What a multiplier may pay in all. */
        BUDGET("budget");

        private final String key;

        Field(String key) {
            this.key = key;
        }

        /**
         * @return the name of the field in commands and inputs
         */
        public String key() {
            return key;
        }

        /**
         * @return the locale key of its label
         */
        public String labelKey() {
            return "companyadmin.event.field." + key;
        }

        /**
         * @return the locale key of the error for an invalid value
         */
        public String errorKey() {
            return "companyadmin.event.error." + key;
        }

        /**
         * @param key the name of a field
         * @return the field, empty when there is none of that name
         */
        public static Optional<Field> of(String key) {
            return Arrays.stream(values())
                    .filter(field -> field.key.equalsIgnoreCase(key.strip()))
                    .findFirst();
        }
    }

    /**
     * The outcome of reading the form.
     */
    public sealed interface Result permits Valid, Invalid {}

    /**
     * Every value is valid.
     *
     * @param event the event to create
     */
    public record Valid(ServerEvent event) implements Result {}

    /**
     * A value is invalid.
     *
     * @param field the field holding it
     */
    public record Invalid(Field field) implements Result {}
}
