/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import org.bukkit.Chunk;
import org.bukkit.block.BlockState;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The board signs in loaded chunks of this server, by position, with the spec each one stores.
 * <p>
 * Filled from the tile entities of a chunk when it loads and emptied for it when it unloads, so the refresh only
 * touches signs that exist and are loaded. Used on the main thread only.
 */
public final class SignBoardIndex {
    private final BoardSignData data;
    private final Map<BoardLocation, String> boards = new HashMap<>();

    /**
     * @param data reads the board of a sign
     */
    public SignBoardIndex(BoardSignData data) {
        this.data = data;
    }

    /**
     * Adds the board signs among the tile entities of a loaded chunk.
     *
     * @param tileEntities tile entities of the chunk
     */
    public void load(Collection<? extends BlockState> tileEntities) {
        for (var state : tileEntities) {
            data.read(state).ifPresent(spec -> boards.put(BoardLocation.of(state.getBlock()), spec));
        }
    }

    /**
     * Removes every board sign of an unloaded chunk.
     *
     * @param chunk the chunk
     */
    public void unload(Chunk chunk) {
        boards.keySet().removeIf(location -> location.in(chunk));
    }

    /**
     * @param location position of the sign
     * @param spec     spec of the board
     */
    public void put(BoardLocation location, String spec) {
        boards.put(location, spec);
    }

    /**
     * @param location position of a former board sign
     */
    public void remove(BoardLocation location) {
        boards.remove(location);
    }

    /**
     * @param location position of a sign
     * @return the spec of the board there, empty when no board sign is indexed there
     */
    public Optional<String> spec(BoardLocation location) {
        return Optional.ofNullable(boards.get(location));
    }

    /**
     * @return a copy of the indexed board signs
     */
    public Map<BoardLocation, String> boards() {
        return Map.copyOf(boards);
    }

    /**
     * @return the distinct specs of the indexed signs, which is what has to be counted
     */
    public Set<String> specs() {
        return new LinkedHashSet<>(boards.values());
    }

    /**
     * @return number of indexed board signs
     */
    public int size() {
        return boards.size();
    }
}
