/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteJobData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.jobs.hooks.JobHooks;
import de.eldoria.companies.jobs.hooks.SchematicCleaner;
import de.eldoria.companies.jobs.hooks.SelectionHook;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uploaded schematics: kept only while the server takes them, cleaned before they are stored, refused when their name,
 * size or count is wrong or they can not be read, and read back as blueprints by their name.
 */
class SchematicUploadsTest {
    private static final byte[] SCHEMATIC = SchematicHeaderTest.sponge(2, 2, 2);

    @TempDir
    Path directory;

    private final UUID owner = UUID.randomUUID();
    private final JobSettings settings = new JobSettings();
    private final List<Path> cleaned = new ArrayList<>();
    private SchematicUploads uploads;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        var hooks = new JobHooks(
                name -> name.equals("FastAsyncWorldEdit"),
                className -> className.contains("Sanitizer") ? new CopyCleaner() : new FileSelections(),
                Logger.getAnonymousLogger());
        hooks.load();
        settings.schematicUploads(true);
        settings.maxSchematics(2);
        uploads = new SchematicUploads(
                new SqLiteJobData(),
                hooks,
                () -> settings,
                () -> LocalDateTime.of(2026, 11, 2, 12, 0),
                Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aCleanedSchematicIsKeptAndReadBackByItsName() {
        var stored = assertInstanceOf(SchematicUploads.Uploaded.Stored.class, uploads.upload(owner, "Barn", SCHEMATIC));

        assertEquals("barn", stored.name());
        assertEquals(new SchematicCleaner.Cleaned(1, 2, 3), stored.cleaned());
        assertEquals(1, stored.blocks());
        assertEquals(1, cleaned.size());
        assertEquals(List.of("barn"), uploads.names(owner));
        assertTrue(uploads.blueprint(owner, "BARN", 0, 100).isPresent());
        assertTrue(uploads.blueprint(UUID.randomUUID(), "barn", 0, 100).isEmpty());
        assertTrue(uploads.delete(owner, "barn"));
        assertEquals(List.of(), uploads.names(owner));
    }

    @Test
    void aBoxLargerThanBuildJobsIsRefusedBeforeItIsCleaned() {
        settings.buildMaxVolume(1000);

        assertEquals(
                refused(SchematicUploads.Reason.UNREADABLE),
                uploads.upload(owner, "tower", SchematicHeaderTest.sponge(10, 11, 10)));
        assertEquals(List.of(), cleaned);
    }

    @Test
    void mcEditSchematicsAreHandedOnUnderTheirOwnSuffix() {
        uploads.upload(owner, "old", SchematicHeaderTest.mcEdit(1, 1, 1));

        assertTrue(cleaned.getFirst().getFileName().toString().endsWith(".schematic"));
    }

    @Test
    void uploadsAreRefusedForTheirNameSizeCountOrContent() {
        uploads.upload(owner, "one", SCHEMATIC);
        uploads.upload(owner, "two", SCHEMATIC);

        assertEquals(refused(SchematicUploads.Reason.NAME), uploads.upload(owner, "bad name!", SCHEMATIC));
        assertEquals(
                refused(SchematicUploads.Reason.TOO_LARGE),
                uploads.upload(owner, "big", new byte[SchematicUploads.MAX_BYTES + 1]));
        assertEquals(refused(SchematicUploads.Reason.LIMIT), uploads.upload(owner, "three", SCHEMATIC));
        assertInstanceOf(SchematicUploads.Uploaded.Stored.class, uploads.upload(owner, "two", SCHEMATIC));
        uploads.delete(owner, "two");
        assertEquals(
                refused(SchematicUploads.Reason.UNREADABLE),
                uploads.upload(owner, "empty", "nothing".getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of("one"), uploads.names(owner));
    }

    @Test
    void nothingIsTakenWhileUploadsAreOff() {
        settings.schematicUploads(false);

        assertFalse(uploads.open());
        assertEquals(refused(SchematicUploads.Reason.OFF), uploads.upload(owner, "barn", SCHEMATIC));
        assertEquals(List.of(), uploads.names(owner));
    }

    private static SchematicUploads.Uploaded refused(SchematicUploads.Reason reason) {
        return new SchematicUploads.Uploaded.Refused(reason);
    }

    /**
     * Copies the upload as it is and reports a fixed cleaning.
     */
    private final class CopyCleaner implements SchematicCleaner {
        @Override
        public Cleaned clean(Path in, Path out) throws IOException {
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            cleaned.add(in);
            return new Cleaned(1, 2, 3);
        }
    }

    /**
     * Reads the file {@link #SCHEMATIC} as a blueprint of one stone block, and anything else as nothing.
     */
    private static final class FileSelections implements SelectionHook {
        @Override
        public Optional<JobArea> selection(Player player) {
            return Optional.empty();
        }

        @Override
        public Optional<Blueprint> schematic(Path file, int rotation, int maxVolume) throws IOException {
            if (!Arrays.equals(Files.readAllBytes(file), SCHEMATIC)) return Optional.empty();
            return Optional.of(Blueprint.of(List.of(new Blueprint.Cell(0, 0, 0, Material.STONE))));
        }
    }
}
