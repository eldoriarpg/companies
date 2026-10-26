/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.SettingsCatalogue;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.dashboard.views.Payloads;
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
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSettingsWritesTest {
    private static final YAMLMapper YAML = YAMLMapper.builder().build();

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

    private static List<String> keys(String file, String before, String after) {
        return AdminSettingsWrites.diff(file, YAML.readTree(before), YAML.readTree(after)).stream()
                .map(Payloads.AdminSettingDiff::key)
                .toList();
    }

    @Test
    void emptyOrReplacedSectionsAreDiffedSettingBySetting() {
        assertEquals(
                List.of("alerts.enabled", "alerts.maxPerCompany"),
                keys("company.yml", "alerts:\n", "alerts:\n  enabled: true\n  maxPerCompany: 3\n"));
        assertEquals(List.of("alerts.enabled"), keys("company.yml", "alerts: 5\n", "alerts:\n  enabled: true\n"));
        assertEquals(
                List.of("dashboard.features"),
                keys(
                        "config.yml",
                        "dashboard:\n  features:\n    orders.browse: true\n",
                        "dashboard:\n  features:\n    orders.browse: false\n    market: false\n"));
    }

    private ActionResult change(String action, String args) {
        return perform(action, args, Permission.Admin.ADMIN, Permission.Admin.RELOAD);
    }

    private String latest(String file) {
        return world.nodeData.latestSettings(file).orElseThrow().content();
    }

    @Test
    void changingNeedsTheReloadPermission() {
        assertEquals(
                "companyadmin.error.permission",
                read(Actions.ADMIN_SETTINGS_SAVE, "{\"file\":\"user.yml\",\"base\":0,\"values\":{\"maxOrders\":3}}")
                        .reason());
    }

    @Test
    void everyBadValueIsNamedAndNothingIsStored() {
        world.nodeData.publishSettings("config.yml", copy(Configuration.CONFIG_YML), "startup");

        var result = change(
                Actions.ADMIN_SETTINGS_SAVE,
                "{\"file\":\"config.yml\",\"base\":1,\"values\":{\"generalSettings.language\":\"xx_XX\","
                        + "\"boards.enabled\":\"yes\",\"top.cacheSeconds\":1.5,\"nope\":1}}");

        assertEquals("invalid", result.data().path("status").asString());
        var fields = result.data().path("fields");
        assertEquals("choice", fields.path("generalSettings.language").asString());
        assertEquals("flag", fields.path("boards.enabled").asString());
        assertEquals("whole", fields.path("top.cacheSeconds").asString());
        assertEquals("unknown", fields.path("nope").asString());
        assertEquals(1, world.nodeData.settingsRevisions().get("config.yml"));
    }

    @Test
    void valuesAreStoredAsTheNextRevisionAndApplied() {
        world.nodeData.publishSettings("config.yml", copy(Configuration.CONFIG_YML), "startup");

        var result = change(
                Actions.ADMIN_SETTINGS_SAVE,
                "{\"file\":\"config.yml\",\"base\":1,\"values\":{\"generalSettings.language\":\"de_DE\","
                        + "\"boards.enabled\":false,\"discord.events\":[\"LEVEL_UP\"]}}");

        assertEquals(
                "saved", result.data().path("status").asString(), result.data().toString());
        assertEquals(2, result.data().path("revision").asInt());
        assertEquals("boards.enabled", result.data().path("restart").get(0).asString());
        assertEquals("de_DE", world.settings.generalSettings().language());
        assertFalse(world.settings.boardSettings().enabled());
        assertTrue(latest("config.yml").contains("de_DE"));
        assertEquals(1, world.settingsApplied.get());
        assertTrue(world.adminActions.stream().anyMatch(line -> line.contains("settings save")));
    }

    @Test
    void aSaveOnAnOutdatedRevisionStoresNothingAndShowsTheNewerValues() {
        var content = copy(Configuration.USER_SETTINGS);
        world.nodeData.publishSettings("user.yml", content, "startup");
        world.nodeData.publishSettings("user.yml", content.replaceFirst("maxOrders: \\d+", "maxOrders: 11"), "Bo");

        var result =
                change(Actions.ADMIN_SETTINGS_SAVE, "{\"file\":\"user.yml\",\"base\":1,\"values\":{\"maxOrders\":3}}");

        assertEquals("newer", result.data().path("status").asString());
        assertEquals(2, result.data().path("revision").asInt());
        assertEquals(11, result.data().path("values").path("maxOrders").asInt());
        assertEquals("maxOrders", result.data().path("changed").get(0).asString());
        assertEquals(2, world.nodeData.settingsRevisions().get("user.yml"));
    }

    @Test
    void aSecretIsKeptWithoutAValueReplacedWithOneAndClearedWithAnEmptyOne() {
        world.nodeData.publishSettings(
                "config.yml",
                copy(Configuration.CONFIG_YML).replaceFirst("url: \"\"", "url: https://a.test/1"),
                "startup");

        change(
                Actions.ADMIN_SETTINGS_SAVE,
                "{\"file\":\"config.yml\",\"base\":1,\"values\":{\"discord.url\":null,\"discord.enabled\":true}}");
        assertTrue(latest("config.yml").contains("https://a.test/1"));

        change(
                Actions.ADMIN_SETTINGS_SAVE,
                "{\"file\":\"config.yml\",\"base\":2,\"values\":{\"discord.url\":\"https://a.test/2\"}}");
        assertTrue(latest("config.yml").contains("https://a.test/2"));

        var cleared = change(
                Actions.ADMIN_SETTINGS_SAVE, "{\"file\":\"config.yml\",\"base\":3,\"values\":{\"discord.url\":\"\"}}");
        assertEquals("saved", cleared.data().path("status").asString());
        assertFalse(latest("config.yml").contains("a.test"));
        assertFalse(cleared.data().toString().contains("a.test"));
    }

    @Test
    void aRollbackStoresTheEarlierRevisionOnTheLatestOne() {
        var content = copy(Configuration.USER_SETTINGS);
        world.nodeData.publishSettings("user.yml", content, "startup");
        world.nodeData.publishSettings("user.yml", content.replaceFirst("maxOrders: \\d+", "maxOrders: 11"), "Bo");

        assertEquals(
                "dashboard.invalid",
                change(Actions.ADMIN_SETTINGS_ROLLBACK, "{\"file\":\"user.yml\",\"revision\":1,\"base\":1}")
                        .reason());
        var result = change(Actions.ADMIN_SETTINGS_ROLLBACK, "{\"file\":\"user.yml\",\"revision\":1,\"base\":2}");

        assertEquals("saved", result.data().path("status").asString());
        assertEquals(3, result.data().path("revision").asInt());
        assertFalse(latest("user.yml").contains("maxOrders: 11"));
    }

    @Test
    void numbersOutsideTheirBoundsUnknownZonesAndBlankSecretsAreRefused() {
        world.nodeData.publishSettings("config.yml", copy(Configuration.CONFIG_YML), "startup");

        var result = change(
                Actions.ADMIN_SETTINGS_SAVE,
                "{\"file\":\"config.yml\",\"base\":1,\"values\":{\"generalSettings.orderCheckInterval\":0,"
                        + "\"generalSettings.timezone\":\"Mars/Base\",\"discord.url\":\"  \","
                        + "\"notifications.pageSize\":null}}");

        var fields = result.data().path("fields");
        assertEquals("range", fields.path("generalSettings.orderCheckInterval").asString());
        assertEquals("timezone", fields.path("generalSettings.timezone").asString());
        assertEquals("secret", fields.path("discord.url").asString());
        assertEquals("empty", fields.path("notifications.pageSize").asString());
        assertEquals(
                "range",
                change(
                                Actions.ADMIN_SETTINGS_SAVE,
                                "{\"file\":\"company.yml\",\"base\":0,\"values\":{\"tax.percent\":101}}")
                        .data()
                        .path("fields")
                        .path("tax.percent")
                        .asString());
        assertEquals(
                "invalid",
                change(
                                Actions.ADMIN_SETTINGS_SAVE,
                                "{\"file\":\"config.yml\",\"base\":7,\"values\":{\"top.cacheSeconds\":5}}")
                        .data()
                        .path("fields")
                        .path("")
                        .asString());
    }

    @Test
    void theFileSaysWhetherTheAdminMayChangeItAndARollbackIsLoggedAsOne() {
        var content = copy(Configuration.USER_SETTINGS);
        world.nodeData.publishSettings("user.yml", content, "startup");
        world.nodeData.publishSettings("user.yml", content.replaceFirst("maxOrders: \\d+", "maxOrders: 11"), "Bo");

        assertFalse(read(Actions.ADMIN_SETTINGS_GET, "{\"file\":\"user.yml\"}")
                .data()
                .path("editable")
                .asBoolean());
        var editable = change(Actions.ADMIN_SETTINGS_GET, "{\"file\":\"user.yml\"}");
        assertTrue(editable.data().path("editable").asBoolean());
        assertEquals(0.0, setting(editable, "maxOrders").path("min").asDouble());

        change(Actions.ADMIN_SETTINGS_ROLLBACK, "{\"file\":\"user.yml\",\"revision\":1,\"base\":2}");
        assertTrue(world.adminActions.stream().anyMatch(line -> line.contains("settings rollback")));
    }

    @Test
    void documentedZerosAreAccepted() {
        world.nodeData.publishSettings("company.yml", copy(Configuration.COMPANY_SETTINGS), "startup");

        var result = change(
                Actions.ADMIN_SETTINGS_SAVE,
                "{\"file\":\"company.yml\",\"base\":1,\"values\":{\"alerts.maxPerCompany\":0,"
                        + "\"treasury.logRetentionDays\":0}}");

        assertEquals(
                "saved", result.data().path("status").asString(), result.data().toString());
        assertEquals(
                0.0,
                SettingsCatalogue.field("order.yml", "solo.maxOrderSize")
                        .orElseThrow()
                        .min());
    }

    private static String png(int width, int height, int size) {
        var bytes = ByteBuffer.allocate(size);
        bytes.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        bytes.putInt(13).put("IHDR".getBytes(StandardCharsets.US_ASCII));
        bytes.putInt(width).putInt(height);
        return Base64.getEncoder().encodeToString(bytes.array());
    }

    @Test
    void anUploadedIconIsStoredAndNamedInTheNextRevision() {
        world.nodeData.publishSettings("config.yml", copy(Configuration.CONFIG_YML), "startup");
        var icon = png(64, 64, 300);

        var result =
                change(Actions.ADMIN_SETTINGS_ICON, "{\"file\":\"config.yml\",\"base\":1,\"png\":\"" + icon + "\"}");

        assertEquals(
                "saved", result.data().path("status").asString(), result.data().toString());
        var named = world.settings.dashboardSettings().branding().icon();
        assertTrue(named.startsWith("shared:icon-"), named);
        assertEquals(
                icon, world.nodeData.asset(named.substring("shared:".length())).orElseThrow());
    }

    @Test
    void anIconThatIsNoPngOrTooLargeIsRefused() {
        world.nodeData.publishSettings("config.yml", copy(Configuration.CONFIG_YML), "startup");

        for (var icon : List.of(png(512, 64, 300), png(64, 64, 50_000), "bm90IGEgcG5n")) {
            var result = change(
                    Actions.ADMIN_SETTINGS_ICON, "{\"file\":\"config.yml\",\"base\":1,\"png\":\"" + icon + "\"}");
            assertEquals(
                    "icon",
                    result.data().path("fields").path("dashboard.branding.icon").asString());
        }
        assertEquals(1, world.nodeData.settingsRevisions().get("config.yml"));
    }
}
