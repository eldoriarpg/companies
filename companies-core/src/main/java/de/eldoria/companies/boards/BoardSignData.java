/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Reads and writes the board of a sign. The sign itself holds it, as the {@link BoardTarget#spec() spec} under the
 * key {@code companies:board} of its persistent data, so a board needs no database row and moves with the world.
 */
public final class BoardSignData {
    private final NamespacedKey key;

    /**
     * @param plugin the plugin, whose name is the namespace of the key
     */
    public BoardSignData(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "board");
    }

    /**
     * @param holder persistent data holder, usually a sign
     * @return the stored spec, empty when the holder is no board
     */
    public Optional<String> stored(PersistentDataHolder holder) {
        return Optional.ofNullable(holder.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    /**
     * @param holder persistent data holder, usually a sign
     * @param spec   spec of the board
     */
    public void write(PersistentDataHolder holder, String spec) {
        holder.getPersistentDataContainer().set(key, PersistentDataType.STRING, spec);
    }

    /**
     * @param state a block state
     * @return the stored spec, empty when the state is no board sign
     */
    public Optional<String> read(BlockState state) {
        if (!(state instanceof Sign sign)) return Optional.empty();
        return stored(sign);
    }

    /**
     * Reads a block without taking a snapshot of it.
     *
     * @param block a block
     * @return the stored spec, empty when the block is no board sign
     */
    public Optional<String> read(Block block) {
        if (!isSign(block)) return Optional.empty();
        return read(block.getState(false));
    }

    /**
     * Tells signs apart by the name of their material, which covers standing, wall and hanging signs of every wood.
     *
     * @param block a block
     * @return whether the block is a sign of any kind
     */
    public static boolean isSign(Block block) {
        return block.getType().name().endsWith("_SIGN");
    }

    /**
     * @param block a block
     * @return whether the block is a board sign
     */
    public boolean isBoard(Block block) {
        return read(block).isPresent();
    }
}
