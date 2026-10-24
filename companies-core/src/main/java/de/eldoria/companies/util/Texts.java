/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.math.BigDecimal;
import java.time.Duration;

public final class Texts {
    public static final String RIGHT_ARROW = "»»»";
    public static final String LEFT_ARROW = "«««";
    private static final int PROGRESS_SEGMENTS = 10;
    private static final String PROGRESS_SEGMENT = "■";

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

    /**
     * Escapes tags, so text entered by a player is shown as written instead of being parsed as MiniMessage.
     *
     * @param text text entered by a player
     * @return text that renders literally in MiniMessage
     */
    public static String escape(String text) {
        return MiniMessage.miniMessage().escapeTags(text);
    }

    /**
     * Escapes quotes and backslashes, so the content stays inside a single-quoted tag argument such as a hover text.
     *
     * @param argument argument content
     * @return content that can be placed between single quotes of a tag argument
     */
    public static String argument(String argument) {
        return argument.replace("\\", "\\\\").replace("'", "\\'");
    }

    /**
     * Draws a progress bar of ten segments, filled ones in the add colour and the rest in the inactive colour.
     *
     * @param progress progress from 0 to 1; values outside are clamped and NaN counts as 0
     * @return MiniMessage bar
     */
    public static String progressBar(double progress) {
        var clamped = Double.isNaN(progress) ? 0 : Math.max(0, Math.min(1, progress));
        var filled = (int) Math.round(clamped * PROGRESS_SEGMENTS);
        return "<add>%s<inactive>%s"
                .formatted(PROGRESS_SEGMENT.repeat(filled), PROGRESS_SEGMENT.repeat(PROGRESS_SEGMENTS - filled));
    }

    /**
     * Writes a percentage without trailing zeros, such as {@code 5} or {@code 2.5}.
     *
     * @param percent percentage
     * @return the number as plain text
     */
    public static String percent(double percent) {
        return BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString();
    }
}
