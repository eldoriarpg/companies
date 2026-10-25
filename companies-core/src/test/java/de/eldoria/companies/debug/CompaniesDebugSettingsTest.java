/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.debug;

import de.eldoria.companies.integrations.discord.WebhookRedaction;
import de.eldoria.eldoutilities.debug.DebugSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompaniesDebugSettingsTest {
    private static final String TOKEN = "AbC-123_xyzSECRET";
    private static final String URL = "https://discord.com/api/webhooks/123456789/" + TOKEN;

    private final DebugSettings settings = CompaniesDebugSettings.create();

    @Test
    void webhookTokenIsMaskedInConfigFile() {
        var config = """
                top:
                  cacheSeconds: 30
                discord:
                  enabled: true
                  url: "%s"
                  events: [ORDER_CREATED]
                """.formatted(URL);

        var filtered = settings.applyFilter(config);

        assertFalse(filtered.contains(TOKEN), filtered);
        assertTrue(filtered.contains("cacheSeconds: 30"));
        assertTrue(filtered.contains("events: [ORDER_CREATED]"));
    }

    @Test
    void webhookTokenIsMaskedAnywhere() {
        var filtered = settings.applyFilter("Posting to " + URL + "?wait=true failed");

        assertEquals("Posting to https://discord.com/api/webhooks/123456789/*****?wait=true failed", filtered);
    }

    @Test
    void relayUrlInDiscordSectionIsMasked() {
        var config = """
                discord:
                  enabled: true

                  url: https://relay.example.org/hook/s3cr3t
                generalSettings:
                  url: https://example.org/keep
                """;

        var filtered = settings.applyFilter(config);

        assertFalse(filtered.contains("s3cr3t"), filtered);
        assertTrue(filtered.contains("url: https://example.org/keep"), filtered);
    }

    @Test
    void anEmptyUrlStaysVisible() {
        assertTrue(settings.applyFilter("discord:\n  url: \"\"\n").contains("url: \"\""));
        assertTrue(settings.applyFilter("discord:\n  url: ''\n").contains("url: ''"));
    }

    @Test
    void dashboardKeysAreMaskedAnywhereAndInNodeYml() {
        var line = settings.applyFilter("Stored lyd_AbC-123_test-key for the network");
        var node = settings.applyFilter("nodeType: PRIMARY\ndashboard:\n  serverKey: self-hosted-token\n");

        assertEquals("Stored lyd_***** for the network", line);
        assertEquals("token cst_*****", settings.applyFilter("token cst_Self-Hosted_9"));
        assertFalse(node.contains("self-hosted-token"), node);
        assertTrue(node.contains("nodeType: PRIMARY"));
    }

    @Test
    void defaultFiltersStayActive() {
        var filtered = settings.applyFilter("password: hunter2\n");

        assertFalse(filtered.contains("hunter2"));
    }

    @Test
    void redactionMasksResolvedUrlAndTokens() {
        var redaction = new WebhookRedaction("https://relay.example.org/s3cr3t");

        assertEquals("sent to *****", redaction.redact("sent to https://relay.example.org/s3cr3t"));
        assertFalse(redaction.redact(URL).contains(TOKEN));
    }

    @Test
    void redisPasswordIsMaskedInDatabaseFile() {
        var config = """
                storageType: POSTGRES
                password: dbSecret
                redis:
                  enabled: true
                  host: redis.internal
                  password: "redisSecret"
                  channelPrefix: companies
                """;

        var filtered = settings.applyFilter(config);

        assertFalse(filtered.contains("dbSecret"), filtered);
        assertFalse(filtered.contains("redisSecret"), filtered);
        assertTrue(filtered.contains("host: redis.internal"), filtered);
        assertTrue(filtered.contains("channelPrefix: companies"), filtered);
    }
}
