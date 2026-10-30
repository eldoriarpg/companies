/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.job.JobArea;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * Checks a build job: the share of its blueprint's blocks whose type matches must reach the match percent. Positions the
 * blueprint leaves as air do not count either way.
 */
public final class BuildCheck {
    private BuildCheck() {}

    /**
     * Reads a block type of a world.
     */
    @FunctionalInterface
    public interface Types {
        /**
         * @param x x of the block
         * @param y y of the block
         * @param z z of the block
         * @return its type
         */
        Material at(int x, int y, int z);
    }

    /**
     * Counts the matching blocks, from the top layer down, and notes the highest block that does not match.
     *
     * @param area      the job's area
     * @param blueprint its blueprint
     * @param types     reads the world, on the main thread with the area's chunks loaded
     * @param percent   the share that must match for the job to be done
     * @return the progress: matching and asked blocks, and no block left once enough match
     */
    public static DigCheck.Progress progress(JobArea area, Blueprint blueprint, Types types, int percent) {
        long matched = 0;
        long total = 0;
        int @Nullable [] left = null;
        for (var y = area.maxY(); y >= area.minY(); y--) {
            for (var x = area.minX(); x <= area.maxX(); x++) {
                for (var z = area.minZ(); z <= area.maxZ(); z++) {
                    var wanted = blueprint.at(area, x, y, z);
                    if (wanted == null) continue;
                    total++;
                    if (types.at(x, y, z) == wanted) {
                        matched++;
                    } else if (left == null) {
                        left = new int[] {x, y, z};
                    }
                }
            }
        }
        var enough = total > 0 && matched * 100 >= (long) percent * total;
        return new DigCheck.Progress(matched, total, enough ? null : left);
    }

    /**
     * @param world a world
     * @return a reader of its block types
     */
    public static Types of(World world) {
        return (x, y, z) -> world.getBlockAt(x, y, z).getType();
    }
}
