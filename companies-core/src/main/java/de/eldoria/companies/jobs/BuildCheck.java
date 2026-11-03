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
 * Checks a build job: the share of its blueprint's blocks that match, by type and the states the blueprint fixes, must
 * reach the match percent. Positions the blueprint leaves as air do not count either way, except those it digs out of
 * the ground, which count once they hold air, water or lava.
 */
public final class BuildCheck {
    private BuildCheck() {}

    /**
     * Reads the blocks of a world.
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

        /**
         * @param x x of the block
         * @param y y of the block
         * @param z z of the block
         * @return its block data as text; the type alone unless the reader knows more
         */
        default String data(int x, int y, int z) {
            return Blueprint.key(at(x, y, z));
        }

        /**
         * @param want what a blueprint asks for at a position
         * @param x    x of the block
         * @param y    y of the block
         * @param z    z of the block
         * @return whether the block there is as asked, its block data read only when the blueprint fixes states; a
         *     position to dig out is done when it holds air, water or lava
         */
        default boolean matches(Blueprint.Want want, int x, int y, int z) {
            var type = at(x, y, z);
            if (want.type().isAir()) return type.isAir() || type == Material.WATER || type == Material.LAVA;
            if (type != want.type()) return false;
            return want.fixed().isEmpty() || want.matches(type, data(x, y, z));
        }
    }

    /**
     * Knows the positions of jobs whose owner supplies the materials where a block was placed from the stock, which
     * count as placed whatever stands there now.
     */
    @FunctionalInterface
    public interface Counted {
        /**
         * Counts no position.
         */
        Counted NONE = (job, key) -> false;

        /**
         * @param job id of a job
         * @param key a position, packed as {@code JobAccess.key}
         * @return whether a block placed from the job's stock counts there
         */
        boolean counted(int job, long key);
    }

    /**
     * @param types   reads a world
     * @param job     id of a job
     * @param counted the positions that count as placed
     * @return the reader, with every block the blueprint asks for matching where a block placed from the job's stock
     *     counts
     */
    public static Types counting(Types types, int job, Counted counted) {
        return new Types() {
            @Override
            public Material at(int x, int y, int z) {
                return types.at(x, y, z);
            }

            @Override
            public String data(int x, int y, int z) {
                return types.data(x, y, z);
            }

            @Override
            public boolean matches(Blueprint.Want want, int x, int y, int z) {
                return (!want.type().isAir() && counted.counted(job, JobAccess.key(x, y, z)))
                        || types.matches(want, x, y, z);
            }
        };
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
                    var wanted = blueprint.want(area, x, y, z);
                    if (wanted == null) continue;
                    total++;
                    if (types.matches(wanted, x, y, z)) {
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
        return new Types() {
            @Override
            public Material at(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getType();
            }

            @Override
            public String data(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getBlockData().getAsString();
            }
        };
    }
}
