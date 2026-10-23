/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.DatabaseSettings;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeBusesTest {
    private final List<String> logs = new CopyOnWriteArrayList<>();
    private final Logger logger = logger();

    private Logger logger() {
        var created = Logger.getLogger("NodeBusesTest-" + UUID.randomUUID());
        created.setUseParentHandlers(false);
        created.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logs.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return created;
    }

    private static DatabaseSettings settings(String yaml) {
        return YAMLMapper.builder()
                .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                .build()
                .readValue(yaml, DatabaseSettings.class);
    }

    private NodeBus create(String yaml) {
        return NodeBuses.create(
                settings(yaml), UUID.randomUUID(), true, new MemoryMessageTable(), Runnable::run, name -> null, logger);
    }

    @Test
    void withoutRedisAndLowLatencyTheNodesPoll() {
        try (var bus = create("storageType: MARIADB\n")) {
            assertInstanceOf(DatabaseBus.class, bus);
            assertEquals(NodeBus.Transport.DATABASE, bus.transport());
            assertTrue(logs.isEmpty(), logs.toString());
        }
    }

    @Test
    void lowLatencyReadsTheMessageTableAndSaysTheIntervalsAreAFallback() {
        try (var bus = create("messaging:\n  lowLatency: true\n")) {
            assertInstanceOf(MessageTableBus.class, bus);
            assertEquals(NodeBus.Transport.LOW_LATENCY, bus.transport());
            assertEquals(1, logs.size(), logs.toString());
            assertTrue(logs.getFirst().contains("Low latency mode is on"), logs.getFirst());
            assertTrue(logs.getFirst().contains("notifications.pollSeconds"), logs.getFirst());
        }
    }

    @Test
    void lowLatencyIsIgnoredWhileRedisIsEnabled() {
        try (var bus = create("""
                redis:
                  enabled: true
                  password: env:COMPANIES_TEST_UNSET
                messaging:
                  lowLatency: true
                """)) {
            assertInstanceOf(DatabaseBus.class, bus);
            assertTrue(logs.stream().anyMatch(log -> log.contains("Low latency mode is ignored")), logs.toString());
            assertTrue(logs.stream().anyMatch(log -> log.contains("COMPANIES_TEST_UNSET")), logs.toString());
            assertTrue(logs.stream().noneMatch(log -> log.contains("Low latency mode is on")), logs.toString());
        }
    }
}
