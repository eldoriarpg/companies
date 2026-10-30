/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.job.JobArea;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * Checks a dig job: every block of its area must be cleared, that is air, water or lava, or a block nobody can break,
 * such as bedrock or a barrier. Liquids count as cleared, as digging next to them lets them flow in and the company
 * cannot be asked to pump them out; unbreakable blocks count, as nobody could remove them.
 */
public final class DigCheck {
    private DigCheck() {}

    /**
     * Counts the cleared blocks of an area, from the top layer down, and notes the highest block left.
     *
     * @param area   the area
     * @param blocks reads whether a block is cleared
     * @return the progress
     */
    public static Progress progress(JobArea area, Blocks blocks) {
        long cleared = 0;
        int[] left = null;
        for (var y = area.maxY(); y >= area.minY(); y--) {
            for (var x = area.minX(); x <= area.maxX(); x++) {
                for (var z = area.minZ(); z <= area.maxZ(); z++) {
                    if (blocks.cleared(x, y, z)) {
                        cleared++;
                    } else if (left == null) {
                        left = new int[] {x, y, z};
                    }
                }
            }
        }
        return new Progress(cleared, area.volume(), left);
    }

    /**
     * Reads the blocks of a world. Call it on the main thread with the area's chunks loaded, see {@link JobProgress}.
     *
     * @param world the world
     * @return the reader
     */
    public static Blocks of(World world) {
        return (x, y, z) -> {
            var block = world.getBlockAt(x, y, z);
            return block.isEmpty() || block.isLiquid() || block.getType().getHardness() < 0;
        };
    }

    /**
     * Reads whether a block is cleared.
     */
    @FunctionalInterface
    public interface Blocks {
        /**
         * @param x x of the block
         * @param y y of the block
         * @param z z of the block
         * @return whether it is air, water, lava or unbreakable
         */
        boolean cleared(int x, int y, int z);
    }

    /**
     * How far a dig job is.
     *
     * @param cleared the blocks cleared
     * @param total   the blocks of the area
     * @param left    the highest block not cleared, as x, y and z, null when every block is cleared
     */
    public record Progress(long cleared, long total, int @Nullable [] left) {
        /**
         * @return whether every block is cleared
         */
        public boolean done() {
            return left == null;
        }

        /**
         * @return the share cleared, from 0 to 100, rounded down; 100 only when done
         */
        public int percent() {
            if (done() || total <= 0) return 100;
            return (int) Math.min(99, cleared * 100 / total);
        }

        /**
         * @return the highest block left as {@code x y z}, empty when done
         */
        public String leftText() {
            var block = left;
            return block == null ? "" : block[0] + " " + block[1] + " " + block[2];
        }
    }
}
