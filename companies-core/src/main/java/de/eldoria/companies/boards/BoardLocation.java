/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import org.bukkit.Chunk;
import org.bukkit.block.Block;

import java.util.UUID;

/**
 * The position of a board sign.
 *
 * @param world id of the world
 * @param x     block x
 * @param y     block y
 * @param z     block z
 */
public record BoardLocation(UUID world, int x, int y, int z) {
    private static final int CHUNK_SHIFT = 4;

    /**
     * @param block the sign block
     * @return the position of the block
     */
    public static BoardLocation of(Block block) {
        return new BoardLocation(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * @param chunk a chunk
     * @return whether the position lies in the chunk
     */
    public boolean in(Chunk chunk) {
        return world.equals(chunk.getWorld().getUID()) && chunkX() == chunk.getX() && chunkZ() == chunk.getZ();
    }

    /**
     * @return x of the chunk holding the position
     */
    public int chunkX() {
        return x >> CHUNK_SHIFT;
    }

    /**
     * @return z of the chunk holding the position
     */
    public int chunkZ() {
        return z >> CHUNK_SHIFT;
    }
}
