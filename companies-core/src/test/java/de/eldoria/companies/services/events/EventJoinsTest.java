/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.configuration.elements.events.EventSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteServerEventData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.data.wrapper.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.util.Times;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventJoinsTest {
    @TempDir
    Path directory;

    private final EventSettings settings = new EventSettings();
    private final List<String> inbox = new CopyOnWriteArrayList<>();
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private final UUID player = UUID.randomUUID();
    private SqLiteServerEventData data;
    private ActiveEvents active;
    private EventJoins joins;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        data = new SqLiteServerEventData();
        active = new ActiveEvents(data, Times.UTC);
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        joins = new EventJoins(
                active,
                data,
                () -> settings,
                new EventJoins.Notices() {
                    @Override
                    public void inbox(UUID who, ServerEvent event) {
                        inbox.add(who + ":" + event.name());
                    }

                    @Override
                    public void message(UUID who, List<ServerEvent> events) {
                        events.forEach(event -> messages.add(who + ":" + event.name()));
                    }
                },
                Runnable::run,
                Times.UTC,
                logger);
    }

    @Test
    void theFirstJoinWhileAnEventRunsGoesToTheInboxAndLaterJoinsToTheChat() {
        running("Tax holiday");

        joins.joined(player);
        joins.joined(player);
        joins.joined(player);

        assertEquals(List.of(player + ":Tax holiday"), inbox);
        assertEquals(List.of(player + ":Tax holiday", player + ":Tax holiday"), messages);
    }

    @Test
    void withoutJoinMessagesOnlyTheInboxEntryIsMade() {
        settings.joinMessage(false);
        running("Tax holiday");

        joins.joined(player);
        joins.joined(player);

        assertEquals(1, inbox.size());
        assertTrue(messages.isEmpty());
    }

    @Test
    void nothingIsToldWhileNoEventRuns() {
        var now = LocalDateTime.now(Times.UTC);
        data.create(event("Later", now.plusHours(1), now.plusHours(2)));

        joins.joined(player);

        assertTrue(inbox.isEmpty());
        assertTrue(messages.isEmpty());
    }

    private void running(String name) {
        var now = LocalDateTime.now(Times.UTC);
        var id = data.create(event(name, now.minusHours(1), now.plusHours(1))).orElseThrow();
        data.state(id, EventState.SCHEDULED, EventState.ACTIVE, null, now);
        active.invalidate();
    }

    private static ServerEvent event(String name, LocalDateTime starts, LocalDateTime ends) {
        return ServerEvent.draft(
                name,
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                starts,
                ends,
                null,
                null,
                null,
                null,
                "Nora",
                starts);
    }
}
