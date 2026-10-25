/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.DiscordSettings;
import de.eldoria.companies.configuration.elements.RedisSettings;
import de.eldoria.companies.configuration.elements.dashboard.NodeDashboardSettings;
import dev.chojo.ocular.override.OverrideApplier;
import dev.chojo.ocular.override.Overwrite;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Secrets set through environment variables win over the file, and are never written: not into the file, not into the
 * database copy of synced files, not into debug reports, all of which serialise the settings.
 */
class EnvironmentOverridesTest {
    private final ObjectMapper mapper = YAMLMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    private <T> T read(String yaml, Class<T> type, Map<String, String> environment) {
        var settings = mapper.readValue(yaml, type);
        OverrideApplier.applyOverrides(settings, name -> Optional.ofNullable(environment.get(name)));
        return settings;
    }

    @Test
    void theWebhookFromTheEnvironmentWinsAndIsNeverWritten() {
        var settings = read(
                "url: https://discord.com/api/webhooks/1/fromFile\n",
                DiscordSettings.class,
                Map.of("urlOverride", "https://discord.com/api/webhooks/2/fromEnv"));

        assertEquals("https://discord.com/api/webhooks/2/fromEnv", settings.url());
        var written = mapper.writeValueAsString(settings);
        assertTrue(written.contains("fromFile"), written);
        assertFalse(written.contains("fromEnv"), written);
    }

    @Test
    void theFileValueIsUsedWithoutTheVariable() {
        var settings = read("url: https://discord.com/api/webhooks/1/fromFile\n", DiscordSettings.class, Map.of());

        assertEquals("https://discord.com/api/webhooks/1/fromFile", settings.url());
    }

    @Test
    void theRedisPasswordAndTheDashboardKeyFromTheEnvironmentAreNeverWritten() {
        var redis = read("password: ''\n", RedisSettings.class, Map.of("passwordOverride", "s3cr3t"));
        var dashboard = read("serverKey: ''\n", NodeDashboardSettings.class, Map.of("serverKeyOverride", "lyd_key"));

        assertEquals("s3cr3t", redis.password());
        assertEquals("lyd_key", dashboard.serverKey());
        assertFalse(mapper.writeValueAsString(redis).contains("s3cr3t"));
        assertFalse(mapper.writeValueAsString(dashboard).contains("lyd_key"));
    }

    @Test
    void everySecretIsReadFromItsDocumentedVariable() throws NoSuchFieldException {
        assertVariable(DiscordSettings.class, "urlOverride", "COMPANIES_DISCORD_WEBHOOK");
        assertVariable(RedisSettings.class, "passwordOverride", "COMPANIES_REDIS_PASSWORD");
        assertVariable(NodeDashboardSettings.class, "serverKeyOverride", "COMPANIES_DASHBOARD_KEY");
    }

    /**
     * Asserts that ocular reads the field from the variable: the field names it, the generated supplier exists, and
     * the settings sit below the configuration package, the root ocular applies overrides within.
     */
    private static void assertVariable(Class<?> type, String field, String variable) throws NoSuchFieldException {
        var overwrite = type.getDeclaredField(field).getAnnotation(Overwrite.class);
        assertEquals(variable, overwrite.env()[0].value());
        assertDoesNotThrow(() -> Class.forName(type.getName() + "_OcularOverride"), type.getName());
        assertTrue(type.getPackageName().startsWith(Configuration.class.getPackageName()), type.getName());
    }
}
