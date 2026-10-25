/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import de.eldoria.companies.configuration.ChangeTracker.Setting;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeTrackerTest {
    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private boolean primary = true;
    private final Map<String, String> files = new HashMap<>(Map.of(
            "config.yml", "discord:\n  url: a\nboards:\n  enabled: true\ntop:\n  cache: 5\n",
            "order.yml", "repeating:\n  checkMinutes: 5\nmaxItems: 3\n"));
    private final ChangeTracker tracker =
            new ChangeTracker(List.of("config.yml", "order.yml"), this::tree, () -> primary);

    private Optional<JsonNode> tree(String path) {
        var content = files.get(path);
        return content == null ? Optional.empty() : Optional.of(YAML.readTree(content));
    }

    @Test
    void nothingCountsAsChangedBeforeTheStart() {
        files.put("config.yml", "discord:\n  url: b\n");

        assertEquals(Set.of(), tracker.changes());
        assertEquals(List.of(), tracker.restartPending());
    }

    @Test
    void changesAreNamedOnceAndThenCountAsApplied() {
        tracker.start();
        files.put("config.yml", "discord:\n  url: b\nboards:\n  enabled: true\ntop:\n  cache: 5\n");
        files.put("order.yml", "repeating:\n  checkMinutes: 5\nmaxItems: 4\n");

        assertEquals(
                Set.of(Setting.of("config.yml", "discord.url"), Setting.of("order.yml", "maxItems")),
                tracker.changes());
        assertEquals(Set.of(), tracker.changes());
    }

    @Test
    void restartSettingsStayPendingUntilTheyMatchTheStartAgain() {
        tracker.start();
        files.put("config.yml", "discord:\n  url: a\nboards:\n  enabled: false\ntop:\n  cache: 5\n");
        files.put("order.yml", "repeating:\n  checkMinutes: 9\nmaxItems: 3\n");
        tracker.changes();

        assertEquals(
                List.of(Setting.of("config.yml", "boards.enabled"), Setting.of("order.yml", "repeating.checkMinutes")),
                tracker.restartPending());

        files.put("config.yml", "discord:\n  url: a\nboards:\n  enabled: true\ntop:\n  cache: 5\n");
        files.put("order.yml", "repeating:\n  checkMinutes: 5\nmaxItems: 3\n");
        assertEquals(List.of(), tracker.restartPending());
    }

    @Test
    void aSecondaryNeedsNoRestartForWhatOnlyThePrimaryUses() {
        primary = false;
        tracker.start();
        files.put("config.yml", "discord:\n  url: a\nboards:\n  enabled: false\ntop:\n  cache: 5\n");
        files.put("order.yml", "repeating:\n  checkMinutes: 9\nmaxItems: 3\n");

        assertEquals(List.of(Setting.of("config.yml", "boards.enabled")), tracker.restartPending());
    }

    @Test
    void aFileThatCannotBeWrittenNowIsComparedLater() {
        tracker.start();
        var config = files.remove("config.yml");

        assertEquals(Set.of(), tracker.changes());

        files.put("config.yml", config.replace("url: a", "url: b"));
        assertEquals(Set.of(Setting.of("config.yml", "discord.url")), tracker.changes());
    }

    @Test
    void workRunsOnlyForTheSettingsItDependsOn() {
        var discord = List.of(Setting.of("config.yml", "discord"));
        var urlChanged = Set.of(Setting.of("config.yml", "discord.url"));
        var topChanged = Set.of(Setting.of("config.yml", "top.cache"));

        assertTrue(ChangeTracker.touches(discord, urlChanged));
        assertFalse(ChangeTracker.touches(discord, topChanged));
        assertFalse(ChangeTracker.touches(discord, Set.of(Setting.of("order.yml", "discord"))));
        assertTrue(ChangeTracker.touches(List.of(), topChanged));
        assertFalse(ChangeTracker.touches(List.of(), Set.of()));
    }

    @Test
    void aWholeFileOrSectionChangingCoversItsSettings() {
        var hooks = Setting.of("config.yml", "generalSettings.hooks");

        assertTrue(hooks.covers(Setting.of("config.yml", "generalSettings.hooks.citizens")));
        assertTrue(hooks.covers(Setting.of("config.yml", "generalSettings")));
        assertTrue(hooks.covers(Setting.of("config.yml", "")));
        assertFalse(hooks.covers(Setting.of("config.yml", "generalSettings.hooksExtra")));
    }
}
