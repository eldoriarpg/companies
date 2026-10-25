/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
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
                settings(yaml), UUID.randomUUID(), true, new MemoryMessageTable(), Runnable::run, logger);
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
                  host: 127.0.0.1
                  port: 1
                messaging:
                  lowLatency: true
                """)) {
            assertEquals(NodeBus.Transport.REDIS, bus.transport());
            assertTrue(logs.stream().anyMatch(log -> log.contains("Low latency mode is ignored")), logs.toString());
            assertTrue(logs.stream().noneMatch(log -> log.contains("Low latency mode is on")), logs.toString());
        }
    }

    @Test
    void postgresNotifyNeedsPostgres() {
        try (var bus = create("""
                storageType: MARIADB
                messaging:
                  postgresNotify: true
                """)) {
            assertInstanceOf(DatabaseBus.class, bus);
            assertEquals(1, logs.size(), logs.toString());
            assertTrue(
                    logs.getFirst().contains("messaging.postgresNotify needs the storage type POSTGRES"),
                    logs.getFirst());
        }
    }

    @Test
    void postgresNotifyWithAnotherStorageLeavesLowLatencyModeOn() {
        var transport = NodeBuses.choose(settings("""
                storageType: MARIADB
                messaging:
                  postgresNotify: true
                  lowLatency: true
                """), logger);

        assertEquals(NodeBus.Transport.LOW_LATENCY, transport);
        assertEquals(1, logs.size(), logs.toString());
        assertTrue(logs.getFirst().contains("PostgreSQL notify is ignored"), logs.getFirst());
    }

    @Test
    void postgresNotifyListensAndSaysTheIntervalsAreAFallback() {
        try (var bus = create("""
                storageType: POSTGRES
                port: '1'
                messaging:
                  postgresNotify: true
                """)) {
            assertInstanceOf(PostgresNotifyBus.class, bus);
            assertEquals(NodeBus.Transport.POSTGRES_NOTIFY, bus.transport());
            assertEquals(NodeBus.State.DISCONNECTED, bus.state());
            assertTrue(logs.stream().anyMatch(log -> log.contains("PostgreSQL notify is on")), logs.toString());
            assertTrue(logs.stream().anyMatch(log -> log.contains("notifications.pollSeconds")), logs.toString());
        }
    }

    @Test
    void postgresNotifyWinsOverLowLatencyMode() {
        var transport = NodeBuses.choose(settings("""
                storageType: POSTGRES
                messaging:
                  postgresNotify: true
                  lowLatency: true
                """), logger);

        assertEquals(NodeBus.Transport.POSTGRES_NOTIFY, transport);
        assertEquals(1, logs.size(), logs.toString());
        assertTrue(
                logs.getFirst().contains("Low latency mode is ignored while PostgreSQL notify is enabled"),
                logs.getFirst());
    }

    @Test
    void redisWinsOverPostgresNotifyAndLowLatencyModeWithOneWarning() {
        var transport = NodeBuses.choose(settings("""
                storageType: POSTGRES
                redis:
                  enabled: true
                messaging:
                  postgresNotify: true
                  lowLatency: true
                """), logger);

        assertEquals(NodeBus.Transport.REDIS, transport);
        assertEquals(1, logs.size(), logs.toString());
        assertTrue(
                logs.getFirst().contains("PostgreSQL notify and low latency mode are ignored while Redis is enabled"),
                logs.getFirst());
    }

    @Test
    void aChannelPrefixTooLongForPostgresIsRefused() {
        try (var bus = create("""
                storageType: POSTGRES
                messaging:
                  postgresNotify: true
                  channelPrefix: %s
                """.formatted("c".repeat(PostgresNotifyBus.MAX_CHANNEL_BYTES + 1)))) {
            assertInstanceOf(DatabaseBus.class, bus);
            assertTrue(
                    logs.stream().anyMatch(log -> log.contains("messaging.channelPrefix is longer")), logs.toString());
        }
    }

    @Test
    void theChannelPrefixMovedToTheMessagingSection() {
        assertEquals("companies", settings("storageType: POSTGRES\n").channelPrefix());
        assertEquals("old", settings("redis:\n  channelPrefix: old\n").channelPrefix());
        assertEquals(
                "new",
                settings("redis:\n  channelPrefix: old\nmessaging:\n  channelPrefix: new\n")
                        .channelPrefix());
        assertEquals("new", settings("messaging:\n  channelPrefix: new\n").channelPrefix());
    }

    @Test
    void newFilesNoLongerWriteTheOlderChannelPrefix() {
        var written = YAMLMapper.builder()
                .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                .build()
                .writeValueAsString(new DatabaseSettings());

        assertEquals(1, written.split("channelPrefix", -1).length - 1, written);
        assertTrue(written.contains("postgresNotify: false"), written);
    }
}
