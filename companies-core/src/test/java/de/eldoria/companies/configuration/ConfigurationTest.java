/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.configuration.elements.PlaceholderSettings;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationTest {
    private static final String SHUFFLED = """
            level:
            - level: 3
              levelName: First
            - level: 1
              levelName: Second
            """;
    private static final ObjectMapper MAPPER = YAMLMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    @TempDir
    Path folder;

    private final List<String> warnings = new ArrayList<>();
    private Configuration configuration;

    @BeforeEach
    void setUp() {
        configuration = new Configuration(plugin(folder, logger()));
    }

    private Logger logger() {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return logger;
    }

    @Test
    void theCatalogueNamesEverySharedSettingExactlyOnce() {
        var listed = new ArrayList<SettingsCatalogue.Field>();
        listed.addAll(SettingsCatalogue.FIELDS);
        listed.addAll(SettingsCatalogue.FILE_ONLY);
        assertEquals(
                listed.size(),
                listed.stream()
                        .map(field -> field.file() + " " + field.key())
                        .distinct()
                        .count());
        for (var key : Configuration.SHARED) {
            var file = key.path().toString();
            var tree = configuration.currentTree(file).orElseThrow();
            var keys = new ArrayList<String>();
            leaves("", tree, keys);
            for (var leaf : keys) {
                assertTrue(
                        listed.stream()
                                .anyMatch(field -> field.file().equals(file)
                                        && (leaf.equals(field.key()) || leaf.startsWith(field.key() + "."))),
                        file + " " + leaf + " is neither in the catalogue nor edited in the file only");
            }
            for (var field :
                    listed.stream().filter(field -> field.file().equals(file)).toList()) {
                var node = tree;
                for (var part : field.path()) node = node.path(part);
                assertFalse(node.isMissingNode(), file + " " + field.key() + " is no setting");
            }
        }
    }

    @Test
    void theCatalogueMarksExactlyTheSettingsReadAtStart() {
        for (var field : SettingsCatalogue.FIELDS) {
            var restart = ChangeTracker.RESTART.stream().anyMatch(setting -> setting.covers(field.file(), field.key()));
            assertEquals(restart, field.restart(), field.file() + " " + field.key());
        }
        for (var setting : ChangeTracker.RESTART) {
            assertTrue(
                    SettingsCatalogue.FIELDS.stream()
                            .anyMatch(field -> field.restart() && setting.covers(field.file(), field.key())),
                    setting.toString());
        }
    }

    private static void leaves(String prefix, JsonNode node, List<String> keys) {
        if (!node.isObject()) {
            keys.add(prefix);
            return;
        }
        for (var entry : node.properties()) {
            leaves(prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey(), entry.getValue(), keys);
        }
    }

    @Test
    void everySettingReloadWorkOrARestartDependsOnExists() {
        var settings = new ArrayList<ChangeTracker.Setting>();
        settings.addAll(ChangeTracker.HOOKED);
        settings.addAll(ChangeTracker.RESTART);

        for (var setting : settings) {
            var tree = configuration.currentTree(setting.path()).orElseThrow();
            var node = tree;
            for (var part : setting.key().split("\\.")) node = node.path(part);
            assertFalse(node.isMissingNode(), setting + " is no setting");
        }
        assertTrue(ChangeTracker.RESTART.containsAll(ChangeTracker.PRIMARY_ONLY));
    }

    @Test
    void aServerUsesItsOverridesWithoutChangingTheSharedFile() throws IOException {
        Files.writeString(folder.resolve("node.yml"), """
                nodeType: PRIMARY
                overrides:
                  messageBlocking: false
                  hooks:
                    citizens: false
                  branding:
                    name: Lobby
                """);
        configuration = new Configuration(plugin(folder, logger()));

        assertFalse(configuration.messageBlocking());
        assertFalse(configuration.hooks().citizens());
        assertTrue(configuration.hooks().fancynpcs());
        assertEquals("Lobby", configuration.branding().name());
        assertEquals(
                configuration.dashboardSettings().branding().icon(),
                configuration.branding().icon());
        assertTrue(configuration.generalSettings().messageBlocking());
        var shared = configuration.currentTree("config.yml").orElseThrow();
        assertTrue(shared.path("generalSettings").path("messageBlocking").asBoolean());
        assertTrue(shared.path("generalSettings").path("hooks").path("citizens").asBoolean());
        assertEquals(
                List.of("branding.name", "hooks.citizens", "messageBlocking"),
                configuration.nodeSettings().overrides().set());
    }

    @Test
    void aTypoInAnOverrideIsNamedAndKeepsTheServersRole() throws IOException {
        var uid = UUID.randomUUID();
        Files.writeString(folder.resolve("node.yml"), """
                nodeType: SECONDARY
                nodeUid: %s
                overrides:
                  messageBlocking: maybe
                  boardEnabled: false
                  hooks:
                    citizens: false
                """.formatted(uid));
        configuration = new Configuration(plugin(folder, logger()));

        assertEquals(NodeType.SECONDARY, configuration.nodeSettings().nodeType());
        assertEquals(uid, configuration.nodeSettings().nodeUid());
        assertTrue(configuration.messageBlocking());
        assertFalse(configuration.hooks().citizens());
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("overrides.messageBlocking")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("overrides.boardEnabled")));

        configuration.save(Configuration.NODE_SETTINGS);
        var written = Files.readString(folder.resolve("node.yml"));
        assertTrue(written.contains("maybe"), written);
        assertTrue(written.contains("boardEnabled"), written);
    }

    @Test
    void changingAnOverrideNeedsARestartButAnOverriddenSharedChangeDoesNot() throws IOException {
        Files.writeString(folder.resolve("node.yml"), "nodeType: PRIMARY\noverrides:\n  boardsEnabled: true\n");
        configuration = new Configuration(plugin(folder, logger()));
        configuration.started();

        var config = folder.resolve("config.yml");
        configuration.save(Configuration.CONFIG_YML);
        Files.writeString(config, Files.readString(config).replace("enabled: true", "enabled: false"));
        configuration.reload();
        assertFalse(configuration.boardSettings().enabled());
        assertEquals(List.of(), configuration.restartPending());

        Files.writeString(folder.resolve("node.yml"), "nodeType: PRIMARY\noverrides:\n  messageBlocking: false\n");
        configuration.reload();
        assertTrue(configuration.restartPending().contains(ChangeTracker.MESSAGE_BLOCKING));
    }

    @Test
    void emptySectionsUseTheirDefaults() throws IOException {
        Files.writeString(folder.resolve("config.yml"), "generalSettings:\n  hooks:\nplaceholders:\nboards:\n");
        configuration = new Configuration(plugin(folder, logger()));
        configuration.started();

        assertTrue(configuration.hooks().citizens());
        assertTrue(configuration.messageBlocking());
        assertEquals(
                new PlaceholderSettings().offlineCacheSize(),
                configuration.placeholderSettings().offlineCacheSize());
        assertEquals(List.of(), configuration.restartPending());
    }

    @Test
    void unsetOverridesAreNotWrittenToNodeYml() throws IOException {
        Files.writeString(folder.resolve("node.yml"), "nodeType: PRIMARY\noverrides:\n");
        configuration = new Configuration(plugin(folder, logger()));
        configuration.nodeSettings().overrides();
        configuration.save(Configuration.NODE_SETTINGS);

        var written = Files.readString(folder.resolve("node.yml"));
        assertFalse(written.contains("null"), written);
        assertFalse(written.contains("overrides"), written);
        assertFalse(configuration.messageBlocking()
                != configuration.generalSettings().messageBlocking());
    }

    @Test
    void changesAreNamedByTheirDottedKeys() {
        var before = MAPPER.readTree("economy:\n  fee: 2\n  tax: 1\nlevel: [1, 2]\nname: a\n");
        var after = MAPPER.readTree("economy:\n  fee: 3\n  tax: 1\nlevel: [1]\nextra: true\n");

        assertEquals(List.of("economy.fee", "extra", "level", "name"), Configuration.changedKeys(before, after));
    }

    @Test
    void anUnreadableSharedFileIsLeftAsItIsAndNeitherSavedNorReplaced() throws IOException {
        var broken = "level: [ {levelName: First";
        Files.writeString(folder.resolve("company.yml"), broken);

        var settings = configuration.companySettings();

        assertTrue(configuration.unreadable(Configuration.COMPANY_SETTINGS));
        assertEquals(new CompanySettings().level().size(), settings.level().size());
        configuration.save(Configuration.COMPANY_SETTINGS);
        assertEquals(broken, Files.readString(folder.resolve("company.yml")));
        try (var files = Files.list(folder)) {
            assertTrue(files.noneMatch(file -> file.getFileName().toString().startsWith("backup_")));
        }

        Files.writeString(folder.resolve("company.yml"), SHUFFLED);
        configuration.reload(Configuration.COMPANY_SETTINGS);
        assertFalse(configuration.unreadable(Configuration.COMPANY_SETTINGS));
        assertEquals(List.of("First", "Second"), names(configuration.companySettings()));
    }

    @Test
    void aFileThatBreaksLaterKeepsItsLastValuesOnReload() throws IOException {
        Files.writeString(folder.resolve("company.yml"), SHUFFLED);
        configuration.companySettings();

        Files.writeString(folder.resolve("company.yml"), "level: [ {levelName: First");
        configuration.reload(Configuration.COMPANY_SETTINGS);

        assertTrue(configuration.unreadable(Configuration.COMPANY_SETTINGS));
        assertEquals(List.of("First", "Second"), names(configuration.companySettings()));
    }

    @Test
    void levelsAreRenumberedInListOrderWhenRead() throws IOException {
        Files.writeString(folder.resolve("company.yml"), SHUFFLED);

        var settings = configuration.companySettings();

        assertEquals(List.of("First", "Second"), names(settings));
        assertEquals(List.of(1, 2), numbers(settings));
        assertEquals(List.of(1, 2), numbers(saved()));
        assertEquals(1, warnings.size());
    }

    @Test
    void levelsAreRenumberedOnReload() throws IOException {
        configuration.companySettings();
        Files.writeString(folder.resolve("company.yml"), SHUFFLED);

        configuration.reload();

        assertEquals(List.of(1, 2), numbers(configuration.companySettings()));
        assertEquals(List.of(1, 2), numbers(saved()));
    }

    @Test
    void levelsInOrderAreLeftAlone() {
        configuration.companySettings();

        assertTrue(warnings.isEmpty(), String.join("\n", warnings));
    }

    private CompanySettings saved() throws IOException {
        return MAPPER.readValue(Files.readString(folder.resolve("company.yml")), CompanySettings.class);
    }

    private static List<String> names(CompanySettings settings) {
        return settings.level().stream().map(CompanyLevel::levelName).toList();
    }

    private static List<Integer> numbers(CompanySettings settings) {
        return settings.level().stream().map(CompanyLevel::level).toList();
    }

    private static Plugin plugin(Path folder, Logger logger) {
        return (Plugin) Proxy.newProxyInstance(
                ConfigurationTest.class.getClassLoader(), new Class<?>[] {Plugin.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getDataFolder" -> folder.toFile();
                        case "getLogger" -> logger;
                        case "getName" -> "Companies";
                        default -> null;
                    };
                });
    }
}
