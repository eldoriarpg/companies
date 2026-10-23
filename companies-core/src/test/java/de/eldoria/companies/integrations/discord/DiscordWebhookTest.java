/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.DiscordSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordWebhookTest {
    private static final ObjectMapper MAPPER = YAMLMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    private final List<WebhookClient> clients = new ArrayList<>();
    private final List<DiscordSettings> connected = new ArrayList<>();
    private DiscordSettings settings = settings("enabled: false");
    private DiscordWebhook webhook;

    @AfterEach
    void stop() {
        if (webhook != null) webhook.stop();
        clients.forEach(client -> client.stop(Duration.ZERO));
    }

    @Test
    void restartReadsTheSettingsAgain() {
        webhook = webhook();
        webhook.restart();
        assertFalse(webhook.running());

        settings = settings("enabled: true\nusername: Bank");
        webhook.restart();

        assertTrue(webhook.running());
        assertEquals(2, connected.size());
        assertEquals(Optional.of("Bank"), connected.getLast().username());
    }

    @Test
    void restartBringsBackAWebhookDiscordRefused() throws InterruptedException {
        settings = settings("enabled: true");
        webhook = webhook();
        webhook.restart();
        clients.getFirst().deliver(List.of(new Embed("one", "", 0, List.of(), Instant.EPOCH)));
        assertFalse(webhook.running());

        webhook.restart();

        assertTrue(webhook.running());
        assertEquals(2, clients.size());
    }

    @Test
    void disablingStopsTheClient() {
        settings = settings("enabled: true");
        webhook = webhook();
        webhook.restart();

        settings = settings("enabled: false");
        webhook.restart();

        assertFalse(webhook.running());
    }

    private DiscordWebhook webhook() {
        return new DiscordWebhook(
                () -> settings,
                this::connect,
                new DiscordEmbeds(key -> key, InstantSource.fixed(Instant.EPOCH)),
                amount -> "$" + amount,
                Logger.getAnonymousLogger());
    }

    private Optional<WebhookClient> connect(DiscordSettings current) {
        connected.add(current);
        if (!current.enabled()) return Optional.empty();
        var client = new WebhookClient(
                json -> new WebhookResponse(404, "", Map.of()),
                new WebhookPayload(current.username(), current.avatarUrl()),
                new WebhookRedaction("https://discord.com/api/webhooks/1/token"),
                new WebhookClient.Limits(current.queueSize(), current.maxPerMinute()),
                InstantSource.system(),
                Sleeper.THREAD,
                Logger.getAnonymousLogger());
        clients.add(client);
        return Optional.of(client);
    }

    private static DiscordSettings settings(String yaml) {
        return MAPPER.readValue(yaml, DiscordSettings.class);
    }
}
