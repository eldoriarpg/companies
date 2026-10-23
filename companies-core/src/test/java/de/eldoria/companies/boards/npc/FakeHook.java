/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards.npc;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A hook of a made up NPC plugin knowing a fixed set of NPCs.
 */
public final class FakeHook implements NpcHook {
    private final String name;
    private final Set<String> npcs;
    private final List<NpcClickHandler> handlers = new ArrayList<>();
    private int disabled;

    /**
     * A hook named {@code Citizens} without NPCs, created by reflection.
     */
    public FakeHook() {
        this("Citizens", Set.of());
    }

    FakeHook(String name, Set<String> npcs) {
        this.name = name;
        this.npcs = npcs;
    }

    @Override
    public String pluginName() {
        return name;
    }

    @Override
    public void enable(Plugin plugin, NpcClickHandler handler) {
        handlers.add(handler);
    }

    @Override
    public void disable() {
        disabled++;
    }

    @Override
    public Optional<String> resolve(CommandSender sender, String argument) {
        return npcs.contains(argument) ? Optional.of(argument) : Optional.empty();
    }

    @Override
    public List<String> suggestions() {
        return List.copyOf(npcs);
    }

    List<NpcClickHandler> handlers() {
        return handlers;
    }

    int disabled() {
        return disabled;
    }
}
