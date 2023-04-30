/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import java.time.Duration;

public final class Texts {
    public static final String RIGHT_ARROW = "»»»";
    public static final String LEFT_ARROW = "«««";

    private Texts() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    public static String prettyDuration(Duration duration) {
        if (duration.toDaysPart() != 0L) {
            return "%s:%s:%s".formatted(duration.toDaysPart(), duration.toHoursPart(), duration.toMinutesPart());
        }
        if (duration.toHoursPart() != 0) {
            return "%s:%s".formatted(duration.toHoursPart(), duration.toMinutesPart());
        }
        if (duration.toMinutesPart() != 0) {
            return "%s".formatted(duration.toMinutesPart());
        }

        return "0";
    }

    public static String trimLeft(String text, int length) {
        if (text == null) return null;
        return text.substring(0, Math.min(length, text.length()));
    }
}
