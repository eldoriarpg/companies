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
import de.eldoria.companies.data.wrapper.job.StoredArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Marked corners and stored areas: two corners in one world span an area, a stored area is kept under a checked name up
 * to the player's limit and replaced by name, and a stored area can be marked again.
 */
class StoredAreasTest {
    private static final UUID NODE = new UUID(0, 1);

    @TempDir
    Path directory;

    private final UUID player = UUID.randomUUID();
    private final JobSettings settings = new JobSettings();
    private StoredAreas areas;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        settings.maxAreas(2);
        areas = new StoredAreas(new SqLiteJobData(), () -> settings, () -> LocalDateTime.of(2026, 11, 2, 12, 0));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void twoCornersInOneWorldSpanTheMarkedArea() {
        assertEquals(Optional.empty(), areas.mark(player, false, new StoredAreas.Corner("world", 5, 70, 5)));
        assertEquals(Optional.empty(), areas.mark(player, true, new StoredAreas.Corner("nether", 0, 60, 0)));

        var marked = areas.mark(player, true, new StoredAreas.Corner("world", 0, 60, 9));

        assertEquals(Optional.of(new JobArea("world", 0, 60, 5, 5, 70, 9)), marked);
        assertEquals(marked, areas.marked(player));
        assertEquals(Optional.empty(), areas.marked(UUID.randomUUID()));
    }

    @Test
    void aStoredAreaCanBeMarkedAgain() {
        var field = new JobArea("world", 0, 60, 0, 9, 64, 9);

        areas.mark(player, field);

        assertEquals(Optional.of(field), areas.marked(player));
    }

    @Test
    void areasAreStoredUnderACheckedNameUpToTheLimit() {
        var field = new JobArea("world", 0, 60, 0, 9, 64, 9);
        var barn = new JobArea("world", 20, 60, 20, 29, 70, 29);

        var stored = assertInstanceOf(StoredAreas.Stored.Done.class, areas.save(player, " Field ", NODE, field));
        areas.save(player, "barn", NODE, barn);
        var refusedName = areas.save(player, "my field!", NODE, field);
        var refusedLimit = areas.save(player, "third", NODE, field);
        var replaced = areas.save(player, "FIELD", NODE, barn);

        assertEquals(new StoredArea("field", NODE, field), stored.area());
        assertEquals(new StoredAreas.Stored.Refused(StoredAreas.Refusal.NAME), refusedName);
        assertEquals(new StoredAreas.Stored.Refused(StoredAreas.Refusal.LIMIT), refusedLimit);
        assertInstanceOf(StoredAreas.Stored.Done.class, replaced);
        assertEquals(
                List.of("barn", "field"),
                areas.areas(player).stream().map(StoredArea::name).toList());
        assertEquals(barn, areas.area(player, "Field").orElseThrow().area());
        assertEquals(true, areas.delete(player, "barn"));
        assertEquals(1, areas.areas(player).size());
    }
}
