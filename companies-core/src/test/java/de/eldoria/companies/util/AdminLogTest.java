/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminLogTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void lineNamesAdminActionAndDetailsInOrder() {
        var log = new AdminLog(Logger.getAnonymousLogger());
        var admin = server.addPlayer("Steve");

        var line = log.entry(admin, "order cancel")
                .with("id", 42)
                .with("refund", true)
                .with("owner", "Alex")
                .with("company", 7)
                .text();

        assertEquals("[admin] Steve order cancel id=42 refund=true owner=Alex company=7", line);
    }

    @Test
    void valuesWithSpacesAreQuotedAndMissingValuesShowADash() {
        var log = new AdminLog(Logger.getAnonymousLogger());

        var line = log.entry(server.getConsoleSender(), "rename")
                .with("from", "Iron Works")
                .with("to", "say \"hi\"")
                .with("company", null)
                .with("note", "")
                .text();

        assertEquals("[admin] CONSOLE rename from=\"Iron Works\" to=\"say \\\"hi\\\"\" company=- note=\"\"", line);
    }

    @Test
    void writeLogsOneInfoLine() {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        List<LogRecord> records = new ArrayList<>();
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });

        new AdminLog(logger)
                .entry(server.addPlayer("Steve"), "grant money")
                .with("amount", 10.5)
                .write();

        assertEquals(1, records.size());
        assertEquals(Level.INFO, records.get(0).getLevel());
        assertEquals("[admin] Steve grant money amount=10.5", records.get(0).getMessage());
    }
}
