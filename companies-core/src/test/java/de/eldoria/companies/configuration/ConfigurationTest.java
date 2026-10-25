/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        configuration = new Configuration(plugin(folder, logger));
    }

    @Test
    void changesAreNamedByTheirDottedKeys() {
        var before = MAPPER.readTree("economy:\n  fee: 2\n  tax: 1\nlevel: [1, 2]\nname: a\n");
        var after = MAPPER.readTree("economy:\n  fee: 3\n  tax: 1\nlevel: [1]\nextra: true\n");

        assertEquals(List.of("economy.fee", "extra", "level", "name"), Configuration.changes(before, after));
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
