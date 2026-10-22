/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Short spans of time such as {@code 2d 3h}, {@code 5h 12m} or {@code 4m}, for ages and remaining lifetimes.
 */
final class TimeSpans {
    private TimeSpans() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * The span between two points in time, in its two largest units. Spans below a minute and negative spans are
     * {@code 0m}.
     *
     * @param from start of the span
     * @param to   end of the span
     * @return the span
     */
    static String between(LocalDateTime from, LocalDateTime to) {
        var span = Duration.between(from, to);
        if (span.isNegative()) return "0m";
        if (span.toDays() > 0) return span.toDays() + "d " + span.toHoursPart() + "h";
        if (span.toHours() > 0) return span.toHours() + "h " + span.toMinutesPart() + "m";
        return span.toMinutes() + "m";
    }
}
