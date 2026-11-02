/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs.hooks;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Cleans a schematic a player uploaded of what no player should get from a build job: the contents of containers,
 * blocks such as command blocks, and creatures.
 */
public interface SchematicCleaner {
    /**
     * What cleaning removed.
     *
     * @param blocks   blocks removed
     * @param data     block and entity data removed, such as the contents of a chest
     * @param entities entities removed
     */
    record Cleaned(int blocks, int data, int entities) {}

    /**
     * @param in  the uploaded schematic
     * @param out where the cleaned schematic is written
     * @return what was removed
     * @throws IOException when the file can not be read as a schematic or the cleaned one can not be written
     */
    Cleaned clean(Path in, Path out) throws IOException;
}
