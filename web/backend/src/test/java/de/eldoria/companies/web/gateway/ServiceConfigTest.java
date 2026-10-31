/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceConfigTest {
    @Test
    void readsSecretsFromFiles(@TempDir Path dir) throws IOException {
        Path password = Files.writeString(dir.resolve("db_password"), "from-file\n");
        Path secret = Files.writeString(dir.resolve("lyna"), "lyg_secret");

        var config = ServiceConfig.of(Map.of(
                "DB_PASSWORD_FILE", password.toString(),
                "LYNA_GATEWAY_SECRET_FILE", secret.toString()));

        assertEquals("from-file", config.database().password());
        assertEquals("lyg_secret", config.lyna().secret());
    }

    @Test
    void variableWinsOverItsFile(@TempDir Path dir) throws IOException {
        Path password = Files.writeString(dir.resolve("db_password"), "from-file");

        var config = ServiceConfig.of(Map.of("DB_PASSWORD", "direct", "DB_PASSWORD_FILE", password.toString()));

        assertEquals("direct", config.database().password());
    }

    @Test
    void emptyFileKeepsTheDefault(@TempDir Path dir) throws IOException {
        Path port = Files.writeString(dir.resolve("port"), " \n");

        assertEquals(
                8080, ServiceConfig.of(Map.of("PORT_FILE", port.toString())).port());
    }

    @Test
    void missingFileFails(@TempDir Path dir) {
        var missing = dir.resolve("missing").toString();

        assertThrows(UncheckedIOException.class, () -> ServiceConfig.of(Map.of("DB_PASSWORD_FILE", missing)));
    }
}
