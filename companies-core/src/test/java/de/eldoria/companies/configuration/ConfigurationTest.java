/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
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
