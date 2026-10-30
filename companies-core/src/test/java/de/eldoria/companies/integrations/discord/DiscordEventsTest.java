/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.DiscordSettings;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.data.wrapper.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordEventsTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);

    private final List<Embed> posted = new ArrayList<>();

    @Test
    void startsAndEndsArePostedWhileTheyAreListed() {
        var events = events("events: [EVENT_STARTED, EVENT_ENDED]");
        var event = multiplier();

        events.changed(event, "ACTIVE");
        events.changed(event, "ENDED");
        events.changed(event, "DEPLETED");
        events.changed(event, "CANCELLED");

        assertEquals(
                List.of(
                        "discord.eventStarted",
                        "discord.eventEnded",
                        "discord.eventDepleted",
                        "discord.eventCancelled"),
                posted.stream().map(Embed::title).toList());
        var fields = posted.getFirst().fields();
        assertTrue(fields.stream().anyMatch(field -> field.value().endsWith("×1.5")));
        assertTrue(fields.stream().anyMatch(field -> field.value().contains("birch log")));
        assertTrue(fields.stream().anyMatch(field -> field.value().equals("$400.0")));
    }

    @Test
    void theStartNamesTheOffersOfTheEvent() {
        events("events: [EVENT_STARTED]").changed(multiplier(), "ACTIVE", List.of("Harbour logs", "Race"));

        assertTrue(posted.getFirst().fields().stream()
                .anyMatch(field ->
                        field.name().equals("words.offers") && field.value().equals("Harbour logs, Race")));
    }

    @Test
    void changesThatAreNotAnnouncedAreNotPosted() {
        var events = events("events: [EVENT_STARTED, EVENT_ENDED]");

        for (var change : List.of("SCHEDULED", "CHANGED", "MISSED", "WITHDRAWN")) events.changed(multiplier(), change);

        assertTrue(posted.isEmpty());
    }

    @Test
    void eventsThatAreNotListedAreNotPosted() {
        var events = events("events: [ORDER_CREATED]");

        events.changed(multiplier(), "ACTIVE");
        events.changed(multiplier(), "ENDED");

        assertTrue(posted.isEmpty());
    }

    private DiscordEvents events(String yaml) {
        var settings = YAMLMapper.builder()
                .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                .build()
                .readValue(yaml, DiscordSettings.class);
        return new DiscordEvents(
                () -> settings,
                new DiscordEmbeds(key -> key, InstantSource.fixed(Instant.EPOCH)),
                amount -> "$" + amount,
                key -> key,
                posted::add);
    }

    private static ServerEvent multiplier() {
        return new ServerEvent(
                3,
                "Harvest",
                EventKind.REWARD_MULTIPLIER,
                1.5,
                List.of("BIRCH_LOG"),
                List.of(),
                NOW,
                NOW.plusDays(1),
                SourceType.MINTED,
                null,
                500.0,
                100.0,
                EventState.ACTIVE,
                null,
                "Nora",
                NOW,
                null,
                null);
    }
}
