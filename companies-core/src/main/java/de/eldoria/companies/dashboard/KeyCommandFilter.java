/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Keeps {@code /companyadmin dashboard key <key>} out of the server log.
 * <p>
 * The server logs every command a player runs, with its arguments, before any plugin sees it. A filter on the root
 * logger drops those lines for the key command, so the key never reaches the log. The filter needs Log4j's core,
 * which the server brings; without it nothing is filtered and a warning says how to enter the key instead.
 */
public final class KeyCommandFilter {
    /**
     * Finds the key command in a log line, with or without the plugin's namespace.
     */
    static final Pattern KEY_COMMAND =
            Pattern.compile("(?i)/?(?:companies:)?companyadmin\\s+dashboard\\s+key(?:\\s|$)");

    private KeyCommandFilter() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Adds the filter to the root logger.
     *
     * @param logger reports when the filter could not be added
     */
    public static void install(Logger logger) {
        try {
            Log4jFilter.addToRoot();
        } catch (LinkageError e) {
            logger.warning("Could not keep /companyadmin dashboard key out of the server log. Enter the key from the "
                    + "console or set it in node.yml.");
        }
    }

    /**
     * Whether a log line shows the key command.
     *
     * @param message the line
     * @return true when the line must be dropped
     */
    static boolean hides(String message) {
        return KEY_COMMAND.matcher(message).find();
    }

    /**
     * The Log4j filter, loaded only when it is added.
     */
    private static final class Log4jFilter extends AbstractFilter {
        private Log4jFilter() {
            super(Filter.Result.DENY, Filter.Result.NEUTRAL);
        }

        static void addToRoot() {
            if (LogManager.getRootLogger() instanceof org.apache.logging.log4j.core.Logger root) {
                root.addFilter(new Log4jFilter());
            }
        }

        @Override
        public Result filter(LogEvent event) {
            var message = event.getMessage();
            if (message == null) return Result.NEUTRAL;
            return hides(message.getFormattedMessage()) ? onMatch : onMismatch;
        }
    }
}
