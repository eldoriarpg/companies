/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import dev.chojo.ocular.key.Key;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSettingsWritesTest {
    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID admin;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        admin = world.player("Ada");
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    private ActionResult perform(String action, String args, String... permissions) {
        var session = new StoredSession(
                "session",
                admin,
                "Ada",
                UUID.randomUUID(),
                Times.now(),
                Times.now(),
                Times.now(),
                Times.now(),
                null,
                Set.of(permissions));
        return world.writes.perform(
                new ActionRequest(
                        "act",
                        Protocol.VERSION,
                        session.id(),
                        admin,
                        action,
                        Protocol.mapper().readTree(args)),
                session);
    }

    private ActionResult read(String action, String args) {
        return perform(action, args, Permission.Admin.ADMIN);
    }

    private String copy(Key<?> key) {
        return world.nodeData.primaryCopy(key, world.settings);
    }

    private static JsonNode setting(ActionResult result, String key) {
        for (var setting : result.data().path("settings")) {
            if (setting.path("key").asString().equals(key)) return setting;
        }
        throw new AssertionError(key + " is not listed");
    }

    @Test
    void theSettingsNeedTheAdminPermissionAndASharedFile() {
        assertEquals(
                "companyadmin.error.permission",
                perform(Actions.ADMIN_SETTINGS_GET, "{\"file\":\"config.yml\"}", Permission.Admin.LEVEL)
                        .reason());
        assertEquals(
                "dashboard.invalid",
                read(Actions.ADMIN_SETTINGS_GET, "{\"file\":\"node.yml\"}").reason());
    }

    @Test
    void aFileIsReadAsItsLatestRevisionWithoutItsSecrets() {
        var content = copy(Configuration.CONFIG_YML)
                .replaceFirst("language: \"?en_US\"?", "language: de_DE")
                .replaceFirst("url: \"\"", "url: https://discord.test/hook");
        world.nodeData.publishSettings("config.yml", content, "Ada");

        var result = read(Actions.ADMIN_SETTINGS_GET, "{\"file\":\"config.yml\"}");

        assertTrue(result.ok(), result.reason());
        assertEquals(1, result.data().path("revision").asInt());
        var language = setting(result, "generalSettings.language");
        assertEquals("de_DE", language.path("value").asString());
        assertEquals("CHOICE", language.path("type").asString());
        assertEquals("general", language.path("section").asString());
        var url = setting(result, "discord.url");
        assertTrue(url.path("value").isNull() || url.path("value").isMissingNode());
        assertTrue(url.path("secret").path("set").asBoolean());
        assertEquals("file", url.path("secret").path("source").asString());
        assertFalse(result.data().toString().contains("discord.test"));
        assertTrue(setting(result, "boards.enabled").path("restart").asBoolean());
    }

    @Test
    void theHistoryListsRevisionsNewestFirst() {
        var content = copy(Configuration.COMPANY_SETTINGS);
        world.nodeData.publishSettings("company.yml", content, "startup");
        world.nodeData.publishSettings(
                "company.yml", content.replace("foundingPrice: 20000.0", "foundingPrice: 500.0"), "Ada");

        var result = read(Actions.ADMIN_SETTINGS_HISTORY, "{\"file\":\"company.yml\",\"page\":1}");

        var revisions = result.data().path("revisions");
        assertEquals(2, revisions.size());
        assertEquals(2, revisions.get(0).path("revision").asInt());
        assertEquals("Ada", revisions.get(0).path("actor").asString());
        assertEquals(
                world.nodeData.nodeUid().toString(),
                revisions.get(0).path("node").asString());
    }

    @Test
    void aRevisionNamesTheSettingsItChangedAsTheCatalogueNamesThem() {
        var content = copy(Configuration.COMPANY_SETTINGS);
        world.nodeData.publishSettings("company.yml", content, "startup");
        var changed = content.replace("foundingPrice: 20000.0", "foundingPrice: 500.0")
                .replace("levelName: \"Startup\"", "levelName: \"Garage\"");
        world.nodeData.publishSettings("company.yml", changed, "Ada");

        var result = read(Actions.ADMIN_SETTINGS_REVISION, "{\"file\":\"company.yml\",\"revision\":2}");

        assertTrue(result.ok(), result.reason());
        var changes = result.data().path("changes");
        assertEquals(2, changes.size(), changes.toString());
        var founding = changes.get(0).path("key").asString().equals("foundingPrice") ? changes.get(0) : changes.get(1);
        var level = founding == changes.get(0) ? changes.get(1) : changes.get(0);
        assertEquals(20000.0, founding.path("before").asDouble());
        assertEquals(500.0, founding.path("after").asDouble());
        assertEquals("level", level.path("key").asString());
        assertTrue(level.path("fileOnly").asBoolean());
        assertTrue(level.path("after").isNull() || level.path("after").isMissingNode());
        assertEquals(
                "companyadmin.settings.error.unknownRevision",
                read(Actions.ADMIN_SETTINGS_REVISION, "{\"file\":\"company.yml\",\"revision\":9}")
                        .reason());
    }

    @Test
    void theFirstRevisionChangesNothingAndAnAddedSectionNamesItsSettings() {
        var content = copy(Configuration.COMPANY_SETTINGS);
        world.nodeData.publishSettings(
                "company.yml", content.replaceFirst("(?s)alerts:.*?(\\n[a-z])", "$1"), "startup");
        world.nodeData.publishSettings("company.yml", content, "Ada");

        var first = read(Actions.ADMIN_SETTINGS_REVISION, "{\"file\":\"company.yml\",\"revision\":1}");
        assertTrue(first.data().path("first").asBoolean());
        assertEquals(0, first.data().path("changes").size());

        var added = read(Actions.ADMIN_SETTINGS_REVISION, "{\"file\":\"company.yml\",\"revision\":2}");
        assertFalse(added.data().path("first").asBoolean());
        var keys = new ArrayList<String>();
        added.data()
                .path("changes")
                .forEach(change -> keys.add(change.path("key").asString()));
        assertTrue(keys.contains("alerts.enabled"), keys.toString());
        assertTrue(keys.contains("alerts.maxPerCompany"), keys.toString());
        assertFalse(keys.contains("alerts"), keys.toString());
    }

    @Test
    void theAnsweringServerIsNamedWithWhatBelongsToIt() {
        world.nodeData.publishSettings("config.yml", copy(Configuration.CONFIG_YML), "startup");

        var result = read(Actions.ADMIN_SETTINGS_GET, "{\"file\":\"config.yml\"}");

        assertEquals(
                world.nodeData.nodeUid().toString(), result.data().path("node").asString());
        assertFalse(setting(result, "discord.url").path("secret").path("set").asBoolean());
        assertEquals(
                "none",
                setting(result, "discord.url").path("secret").path("source").asString());
        assertFalse(setting(result, "generalSettings.messageBlocking")
                .path("overridden")
                .asBoolean());
    }
}
