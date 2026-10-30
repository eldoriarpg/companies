/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs.hooks;

import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.jobs.Blueprint;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads the area a player selected, such as with WorldEdit's {@code //pos1} and {@code //pos2}, on the main thread, and
 * reads schematics for build jobs on any thread.
 */
public interface SelectionHook {
    /**
     * @param player the player
     * @return the selected box, empty when the player selected none or not a box
     */
    Optional<JobArea> selection(Player player);

    /**
     * @param file      a schematic file
     * @param rotation  degrees to turn it clockwise seen from above: 0, 90, 180 or 270
     * @param maxVolume the most blocks its box may hold; a larger one is not read further
     * @return its blocks around its origin, turned, empty when the file is no schematic, holds only air or is too large
     * @throws IOException when the file can not be read
     */
    Optional<Blueprint> schematic(Path file, int rotation, int maxVolume) throws IOException;
}
