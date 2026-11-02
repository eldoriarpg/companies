/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.job;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A block a build job asks for, relative to the point the blueprint is placed at.
 *
 * @param x    x offset
 * @param y    y offset
 * @param z    z offset
 * @param type the block type
 * @param data the block data, null for the type alone; its orientation (facing, half, axis, rotation, hinge, attached
 *             face, a slab's type) must match as well, while states the neighbours decide, like connections, never
 *             count
 */
public record BuildBlock(
        int x, int y, int z, Material type, @Nullable BlockData data) {
    /**
     * Validates the block.
     *
     * @throws IllegalArgumentException when the type is not a block or the data is of another type
     */
    public BuildBlock {
        Objects.requireNonNull(type, "type");
        if (!type.isBlock()) throw new IllegalArgumentException(type + " is not a block");
        if (data != null && data.getMaterial() != type) {
            throw new IllegalArgumentException("The data " + data.getAsString() + " is not of " + type);
        }
    }

    /**
     * A block asked for by its type alone.
     *
     * @param x    x offset
     * @param y    y offset
     * @param z    z offset
     * @param type the block type
     */
    public BuildBlock(int x, int y, int z, Material type) {
        this(x, y, z, type, null);
    }

    /**
     * A block asked for with its block data.
     *
     * @param x    x offset
     * @param y    y offset
     * @param z    z offset
     * @param data the block data, whose type is the block type
     */
    public BuildBlock(int x, int y, int z, BlockData data) {
        this(x, y, z, data.getMaterial(), data);
    }
}
