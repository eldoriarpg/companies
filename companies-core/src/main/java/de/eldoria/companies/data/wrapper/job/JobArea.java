/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.job;

/**
 * The block box of a job in a world, both corners included.
 *
 * @param world name of the world
 * @param minX  lowest x
 * @param minY  lowest y
 * @param minZ  lowest z
 * @param maxX  highest x
 * @param maxY  highest y
 * @param maxZ  highest z
 */
public record JobArea(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    /**
     * Orders the corners, so the minimum is never above the maximum.
     *
     * @param world name of the world
     * @param minX  one x
     * @param minY  one y
     * @param minZ  one z
     * @param maxX  the other x
     * @param maxY  the other y
     * @param maxZ  the other z
     */
    public JobArea {
        var lowX = Math.min(minX, maxX);
        var lowY = Math.min(minY, maxY);
        var lowZ = Math.min(minZ, maxZ);
        maxX = Math.max(minX, maxX);
        maxY = Math.max(minY, maxY);
        maxZ = Math.max(minZ, maxZ);
        minX = lowX;
        minY = lowY;
        minZ = lowZ;
    }

    /**
     * @return how many blocks the box holds
     */
    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /**
     * @return how many chunks the box touches
     */
    public long chunks() {
        return (long) ((maxX >> 4) - (minX >> 4) + 1) * ((maxZ >> 4) - (minZ >> 4) + 1);
    }

    /**
     * @param world name of a world
     * @param x     x of a block
     * @param y     y of a block
     * @param z     z of a block
     * @return whether the block lies in the box
     */
    public boolean contains(String world, int x, int y, int z) {
        return this.world.equals(world) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /**
     * @return the block in the middle of the box's top layer, to point players to it
     */
    public String center() {
        return (minX + maxX) / 2 + " " + maxY + " " + (minZ + maxZ) / 2;
    }
}
