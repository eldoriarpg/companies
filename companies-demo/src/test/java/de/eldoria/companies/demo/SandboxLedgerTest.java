/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxLedgerTest {
    private static final UUID ADA = UUID.randomUUID();
    private static final UUID BEN = UUID.randomUUID();

    @TempDir
    Path directory;

    private SandboxLedger ledger() {
        return new SandboxLedger(directory.resolve("data").resolve("sandbox-players.yml"), Logger.getAnonymousLogger());
    }

    @Test
    void keepsPlayersOverARestart() {
        var ledger = ledger();
        ledger.add(ADA, "Ada");
        ledger.add(BEN, "Ben");
        ledger.remove(BEN);

        var restarted = ledger();

        assertEquals(Map.of(ADA, "Ada"), restarted.all());
        assertTrue(restarted.contains(ADA));
        assertFalse(restarted.contains(BEN));
    }

    @Test
    void startsEmptyWithoutAFileAndSkipsWhatIsNoPlayerOrHasNoName() throws IOException {
        assertTrue(ledger().all().isEmpty());

        Files.createDirectories(directory.resolve("data"));
        Files.writeString(
                directory.resolve("data").resolve("sandbox-players.yml"),
                "nobody: Ada\n" + BEN + ": Ben\n" + ADA + ": ''\n");

        assertEquals(Map.of(BEN, "Ben"), ledger().all());
    }
}
