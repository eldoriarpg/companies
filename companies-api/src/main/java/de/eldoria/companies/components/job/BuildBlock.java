/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.job;

import org.bukkit.Material;

import java.util.Objects;

/**
 * A block a build job asks for, relative to the point the blueprint is placed at.
 *
 * @param x    x offset
 * @param y    y offset
 * @param z    z offset
 * @param type the block type; only types are compared, not block states
 */
public record BuildBlock(int x, int y, int z, Material type) {
    /**
     * Validates the block.
     *
     * @throws IllegalArgumentException when the type is not a block
     */
    public BuildBlock {
        Objects.requireNonNull(type, "type");
        if (!type.isBlock()) throw new IllegalArgumentException(type + " is not a block");
    }
}
