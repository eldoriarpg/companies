/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import de.eldoria.companies.configuration.elements.boardbindings.NpcBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The NPCs bound to boards, the file {@code boards.yml}.
 * <p>
 * NPC ids belong to one server, so this file is not copied between nodes. An NPC is bound to at most one board;
 * binding it again replaces the board.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class BoardBindings {
    private List<NpcBinding> npcs = new ArrayList<>();

    public BoardBindings() {}

    /**
     * @return the bound NPCs in the order they were bound
     */
    public synchronized List<NpcBinding> npcs() {
        if (npcs == null) return List.of();
        return npcs.stream().filter(Objects::nonNull).toList();
    }

    /**
     * @param hook name of the NPC plugin, compared ignoring case
     * @param npc  key of the NPC
     * @return the board of the NPC, empty when it is not bound
     */
    public synchronized Optional<String> board(String hook, String npc) {
        return npcs().stream()
                .filter(binding -> binding.isFor(hook, npc))
                .findFirst()
                .map(NpcBinding::board);
    }

    /**
     * Binds an NPC to a board, replacing an earlier board of the NPC.
     *
     * @param hook  name of the NPC plugin
     * @param npc   key of the NPC
     * @param board preset name or materials separated by spaces, empty for every open order
     */
    public synchronized void link(String hook, String npc, String board) {
        var bindings = new ArrayList<>(npcs());
        bindings.removeIf(binding -> binding.isFor(hook, npc));
        bindings.add(new NpcBinding(hook, npc, board));
        npcs = bindings;
    }

    /**
     * Removes the board of an NPC.
     *
     * @param hook name of the NPC plugin, compared ignoring case
     * @param npc  key of the NPC
     * @return whether the NPC was bound
     */
    public synchronized boolean unlink(String hook, String npc) {
        var bindings = new ArrayList<>(npcs());
        var removed = bindings.removeIf(binding -> binding.isFor(hook, npc));
        npcs = bindings;
        return removed;
    }
}
