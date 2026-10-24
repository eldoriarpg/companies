/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyCommandFilterTest {
    private final LoggerContext context = new LoggerContext("key-command-filter-test");

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void aRealLoggerDropsTheKeyCommandAndKeepsTheRest() {
        var lines = new Capture();
        lines.start();
        var configuration = context.getConfiguration();
        configuration.getRootLogger().addAppender(lines, Level.ALL, null);
        configuration.getRootLogger().setLevel(Level.ALL);
        context.updateLoggers();
        var root = context.getRootLogger();

        KeyCommandFilter.addTo(root);
        context.updateLoggers();
        root.info("Wren issued server command: /companyadmin dashboard key lyd_test-key");
        root.info("Wren issued server command: /companies:companyadmin dashboard key cst_test-token");
        root.info("Wren issued server command: /companyadmin dashboard status");

        assertEquals(List.of("Wren issued server command: /companyadmin dashboard status"), lines.messages);
    }

    /**
     * Keeps the messages logged through it.
     */
    private static final class Capture extends AbstractAppender {
        private final List<String> messages = new CopyOnWriteArrayList<>();

        Capture() {
            super("capture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    }
}
