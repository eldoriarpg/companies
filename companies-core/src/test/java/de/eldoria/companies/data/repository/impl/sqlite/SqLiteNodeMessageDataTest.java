/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.repository.ANodeMessageData.StoredMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqLiteNodeMessageDataTest {
    private static final UUID NODE = UUID.randomUUID();

    @TempDir
    Path directory;

    private SqLiteNodeMessageData messages;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        messages = new SqLiteNodeMessageData();
    }

    @Test
    void anEmptyTableStartsAtZero() {
        assertEquals(0, messages.maxId());
        assertEquals(List.of(), messages.after(0, 10));
    }

    @Test
    void rowsAreReadAfterAnIdInOrderAndInPages() {
        var now = LocalDateTime.now();
        messages.insert(NODE, "inbox.new", "{\"a\":1}", now);
        messages.insert(NODE, "inbox.new", "{\"a\":2}", now);
        messages.insert(NODE, "inbox.new", "{\"a\":3}", now);

        var max = messages.maxId();
        var first = messages.after(0, 2);

        assertEquals(List.of(new StoredMessage(max - 2, "{\"a\":1}"), new StoredMessage(max - 1, "{\"a\":2}")), first);
        assertEquals(
                List.of(new StoredMessage(max, "{\"a\":3}")),
                messages.after(first.getLast().id(), 2));
        assertEquals(List.of(), messages.after(max, 2));
    }

    @Test
    void purgingDeletesOnlyOlderRows() {
        var now = LocalDateTime.now();
        messages.insert(NODE, "inbox.new", "old", now.minusMinutes(2));
        messages.insert(NODE, "inbox.new", "new", now);

        assertEquals(1, messages.purgeBefore(now.minusMinutes(1)));

        assertEquals(
                List.of("new"),
                messages.after(0, 10).stream().map(StoredMessage::payload).toList());
    }

    @Test
    void aFailingQueryThrowsInsteadOfLogging() {
        query("DROP TABLE node_messages").single().update();

        assertThrows(RuntimeException.class, () -> messages.after(0, 10));
        assertThrows(RuntimeException.class, () -> messages.insert(NODE, "inbox.new", "{}", LocalDateTime.now()));
    }
}
