/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.gateway;

import de.eldoria.companies.configuration.elements.dashboard.BrandingSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandingSourceTest {
    @TempDir
    Path plugin;

    @TempDir
    Path server;

    private final Map<String, String> shared = new HashMap<>();

    private static byte[] png(int width, int height, int size) {
        var bytes = ByteBuffer.allocate(size);
        bytes.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        bytes.putInt(13).put("IHDR".getBytes());
        bytes.putInt(width).putInt(height);
        return bytes.array();
    }

    private BrandingSource source(String name, String icon, String motd) {
        return new BrandingSource(
                () -> new BrandingSettings(name, icon),
                () -> motd,
                List.of(plugin, server),
                asset -> Optional.ofNullable(shared.get(asset)),
                Logger.getAnonymousLogger());
    }

    @Test
    void theNameDefaultsToTheMotdAndTheIconToTheServerIcon() throws Exception {
        var icon = png(64, 64, 200);
        Files.write(server.resolve("server-icon.png"), icon);

        var branding = source("", "server-icon.png", "  My   Server  ").read().orElseThrow();

        assertEquals("My Server", branding.name());
        assertEquals(Base64.getEncoder().encodeToString(icon), branding.icon());
        assertEquals(64, branding.iconHash().length());
    }

    @Test
    void anUploadedIconIsReadFromTheDatabaseAndAMissingOneIsLeftOut() throws Exception {
        Files.write(server.resolve("server-icon.png"), png(64, 64, 200));
        shared.put("icon-abc", Base64.getEncoder().encodeToString(png(16, 16, 80)));

        var uploaded = source("Castle", "shared:icon-abc", "motd").read().orElseThrow();
        var missing = source("Castle", "shared:icon-gone", "motd").read().orElseThrow();

        assertEquals(Base64.getEncoder().encodeToString(png(16, 16, 80)), uploaded.icon());
        assertNull(missing.icon());
    }

    @Test
    void anUploadedIconThatCannotBeReadIsLeftOut() {
        var source = new BrandingSource(
                () -> new BrandingSettings("Castle", "shared:icon-abc"),
                () -> "motd",
                List.of(plugin, server),
                asset -> {
                    throw new IllegalStateException("the database is away");
                },
                Logger.getAnonymousLogger());

        assertNull(source.read().orElseThrow().icon());
    }

    @Test
    void aFileInThePluginFolderComesFirst() throws Exception {
        Files.write(server.resolve("logo.png"), png(64, 64, 200));
        Files.write(plugin.resolve("logo.png"), png(32, 32, 100));

        var branding = source("Castle", "logo.png", "motd").read().orElseThrow();

        assertEquals("Castle", branding.name());
        assertEquals(Base64.getEncoder().encodeToString(png(32, 32, 100)), branding.icon());
    }

    @Test
    void tooLargeOrNoPngIsLeftOut() throws Exception {
        Files.write(plugin.resolve("wide.png"), png(512, 64, 200));
        Files.write(plugin.resolve("heavy.png"), png(64, 64, 60_000));
        Files.writeString(plugin.resolve("text.png"), "not a picture at all, really");

        assertNull(source("A", "wide.png", "").read().orElseThrow().icon());
        assertNull(source("A", "heavy.png", "").read().orElseThrow().icon());
        assertNull(source("A", "text.png", "").read().orElseThrow().icon());
        assertTrue(source("", "missing.png", "").read().isEmpty());
    }

    @Test
    void longNamesAreCut() {
        var branding = source("x".repeat(100), "", "").read().orElseThrow();

        assertEquals(64, branding.name().length());
        assertNull(branding.iconHash());
        assertNotNull(branding.name());
    }
}
