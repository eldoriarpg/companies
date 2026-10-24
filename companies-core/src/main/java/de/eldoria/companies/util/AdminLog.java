/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Writes every admin action to the server log, one line per action, naming the admin who performed it:
 * {@code [admin] Steve order cancel id=42 refund=true owner=Alex company="Iron Works"}.
 * <p>
 * Details keep the order they were added in. Values with spaces or quotes are quoted, missing values read {@code -}.
 */
public final class AdminLog {
    private static final Pattern NEEDS_QUOTES = Pattern.compile("[\\s\"=]");

    private final Logger logger;

    /**
     * @param logger logger of the plugin
     */
    public AdminLog(Logger logger) {
        this.logger = logger;
    }

    /**
     * Starts the line of an action.
     *
     * @param admin  who performed the action
     * @param action what was done, such as {@code order cancel}
     * @return the line, written by {@link Entry#write()}
     */
    public Entry entry(CommandSender admin, String action) {
        return new Entry(admin.getName(), action);
    }

    /**
     * Formats one log line.
     *
     * @param admin   name of the admin
     * @param action  what was done
     * @param details named values of the action, in order
     * @return the line
     */
    public static String line(String admin, String action, List<Map.Entry<String, String>> details) {
        var line = new StringBuilder("[admin] ").append(admin).append(' ').append(action);
        for (var detail : details) {
            line.append(' ').append(detail.getKey()).append('=').append(detail.getValue());
        }
        return line.toString();
    }

    /**
     * Formats one value of a detail.
     *
     * @param value value, or null when it is missing
     * @return the value, quoted when it contains spaces, quotes or equal signs
     */
    static String value(@Nullable Object value) {
        if (value == null) return "-";
        var text = String.valueOf(value);
        if (text.isEmpty()) return "\"\"";
        if (!NEEDS_QUOTES.matcher(text).find()) return text;
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /**
     * The log line of one action, written once all details were added.
     */
    public final class Entry {
        private final String admin;
        private final String action;
        private final List<Map.Entry<String, String>> details = new ArrayList<>();

        private Entry(String admin, String action) {
            this.admin = admin;
            this.action = action;
        }

        /**
         * Adds a detail.
         *
         * @param key   name of the detail
         * @param value value, or null when it is missing
         * @return this entry
         */
        public Entry with(String key, @Nullable Object value) {
            details.add(Map.entry(key, value(value)));
            return this;
        }

        /**
         * Writes the line to the log.
         */
        public void write() {
            logger.info(text());
        }

        /**
         * @return the line as it is written
         */
        public String text() {
            return line(admin, action, details);
        }
    }
}
