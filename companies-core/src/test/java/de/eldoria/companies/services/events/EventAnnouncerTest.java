/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.configuration.elements.events.EventSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteServerEventData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.nodes.NodeMessage.ServerEventChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventAnnouncerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);

    @TempDir
    Path directory;

    private final EventSettings settings = new EventSettings();
    private final List<EventAnnouncer.Announcement> told = new CopyOnWriteArrayList<>();
    private EventAnnouncer announcer;
    private int id;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        var data = new SqLiteServerEventData();
        id = data.create(ServerEvent.draft(
                        "Tax holiday",
                        EventKind.TAX_REDUCTION,
                        5,
                        List.of(),
                        List.of(),
                        NOW,
                        NOW.plusDays(1),
                        null,
                        null,
                        null,
                        null,
                        "Nora",
                        NOW))
                .orElseThrow();
        announcer = new EventAnnouncer(data, () -> settings, told::add);
    }

    @Test
    void startsEndsDepletionsAndCancellationsAfterTheStartAreAnnouncedOnceEach() {
        for (var change : List.of("ACTIVE", "ENDED", "DEPLETED", "CANCELLED")) {
            announcer.handle(new ServerEventChanged(id, change));
        }

        assertEquals(
                List.of(
                        "events.announce.started",
                        "events.announce.ended",
                        "events.announce.depleted",
                        "events.announce.cancelled"),
                told.stream().map(EventAnnouncer.Announcement::key).toList());
        assertTrue(told.stream().allMatch(announcement -> announcement.event().id() == id));
    }

    @Test
    void createdChangedMissedAndWithdrawnEventsAreNotAnnounced() {
        for (var change : List.of(
                "SCHEDULED", ServerEventChanged.CHANGED, ServerEventChanged.MISSED, ServerEventChanged.WITHDRAWN)) {
            announcer.handle(new ServerEventChanged(id, change));
        }

        assertTrue(told.isEmpty());
    }

    @Test
    void nothingIsAnnouncedWhileTheBroadcastIsOffOrForAnUnknownEvent() {
        settings.broadcast(false);
        announcer.handle(new ServerEventChanged(id, "ACTIVE"));
        settings.broadcast(true);
        announcer.handle(new ServerEventChanged(id + 10, "ACTIVE"));

        assertTrue(told.isEmpty());
    }
}
