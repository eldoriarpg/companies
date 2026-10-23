/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.boardbindings;

/**
 * An NPC that opens a board when right-clicked.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class NpcBinding {
    private String hook = "";
    private String npc = "";
    private String board = "";

    public NpcBinding() {}

    /**
     * @param hook  name of the NPC plugin, like {@code Citizens}
     * @param npc   the NPC's key in that plugin: the Citizens id or the FancyNpcs name
     * @param board preset name or materials separated by spaces, empty for every open order
     */
    public NpcBinding(String hook, String npc, String board) {
        this.hook = hook;
        this.npc = npc;
        this.board = board;
    }

    /**
     * @return name of the NPC plugin
     */
    public String hook() {
        return hook == null ? "" : hook;
    }

    /**
     * @return the NPC's key in its plugin
     */
    public String npc() {
        return npc == null ? "" : npc;
    }

    /**
     * @return preset name or materials separated by spaces, empty for every open order
     */
    public String board() {
        return board == null ? "" : board;
    }

    /**
     * @param hookName name of an NPC plugin, compared ignoring case
     * @param npcKey   key of an NPC, compared exactly
     * @return whether this binding is for that NPC
     */
    public boolean isFor(String hookName, String npcKey) {
        return hook().equalsIgnoreCase(hookName) && npc().equals(npcKey);
    }
}
