/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
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
    void environmentReferenceAndEmptyUrlStayVisible() {
        assertTrue(settings.applyFilter("discord:\n  url: env:COMPANIES_DISCORD_WEBHOOK\n")
                .contains("url: env:COMPANIES_DISCORD_WEBHOOK"));
        assertTrue(settings.applyFilter("discord:\n  url: \"\"\n").contains("url: \"\""));
        assertTrue(settings.applyFilter("discord:\n  url: ''\n").contains("url: ''"));
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
}
