/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.events.job.JobEndEvent;
import de.eldoria.companies.events.job.JobPostEvent;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The job hooks of the service become Bukkit events, and a cancelled post or take stops the change.
 */
class JobApiEventsTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);

    private ServerMock server;
    private final List<Event> fired = new ArrayList<>();
    private boolean cancel;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aCancelledPostIsRefusedAndAnEndCarriesItsReason() {
        var events = new JobApiEvents(
                event -> {
                    fired.add(event);
                    if (cancel && event instanceof JobPostEvent post) post.setCancelled(true);
                },
                server::getOfflinePlayer);
        var draft = Job.draft(
                UUID.randomUUID(),
                JobKind.DIG,
                "Pit",
                100,
                5,
                UUID.randomUUID(),
                new JobArea("world", 0, 60, 0, 4, 64, 4),
                JobLand.AREA,
                null,
                24,
                NOW,
                NOW.plusDays(7));

        assertTrue(events.allowsPost(draft));
        cancel = true;
        assertFalse(events.allowsPost(draft));
        events.ended(draft.withId(4), JobEndEvent.Reason.EXPIRED);

        var post = assertInstanceOf(JobPostEvent.class, fired.getFirst());
        assertEquals(draft.owner(), post.player().getUniqueId());
        assertEquals(0, post.job().id());
        var end = assertInstanceOf(JobEndEvent.class, fired.getLast());
        assertEquals(4, end.job().id());
        assertEquals(JobEndEvent.Reason.EXPIRED, end.reason());
    }
}
