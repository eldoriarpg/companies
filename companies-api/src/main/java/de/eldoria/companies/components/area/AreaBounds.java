/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.area;

import java.util.List;

/**
 * Where an area lies: one or more boxes of blocks in one world. A plot merged from several plots has a box per part; a
 * region of another shape is given by the box around it.
 *
 * @param world name of the world
 * @param boxes the boxes, at least one
 */
public record AreaBounds(String world, List<Box> boxes) {
    /**
     * Checks that there is a box and keeps a copy of the boxes.
     *
     * @param world name of the world
     * @param boxes the boxes, at least one
     */
    public AreaBounds {
        if (boxes.isEmpty()) throw new IllegalArgumentException("an area needs a box");
        boxes = List.copyOf(boxes);
    }

    /**
     * A box of blocks, both corners included.
     *
     * @param minX lowest x
     * @param minY lowest y
     * @param minZ lowest z
     * @param maxX highest x
     * @param maxY highest y
     * @param maxZ highest z
     */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        /**
         * Whether the box holds a block.
         *
         * @param x x of a block
         * @param y y of a block
         * @param z z of a block
         * @return whether the box holds the block
         */
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    /**
     * Whether the area holds a block.
     *
     * @param world name of a world
     * @param x     x of a block
     * @param y     y of a block
     * @param z     z of a block
     * @return whether the area holds the block
     */
    public boolean contains(String world, int x, int y, int z) {
        if (!this.world.equals(world)) return false;
        for (var box : boxes) if (box.contains(x, y, z)) return true;
        return false;
    }
}
