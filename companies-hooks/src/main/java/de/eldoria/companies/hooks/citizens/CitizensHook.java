/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.citizens;

import de.eldoria.companies.boards.npc.NpcClickHandler;
import de.eldoria.companies.boards.npc.NpcHook;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * NPC boards on Citizens NPCs. An NPC of the main registry is named by its id, or by {@code selected} for the NPC the
 * admin selected with {@code /npc select}.
 */
public final class CitizensHook implements NpcHook {
    private static final String NAME = "Citizens";
    private static final String SELECTED = "selected";
    private static final int MAX_SUGGESTIONS = 100;

    private final AtomicReference<Optional<Listener>> listener = new AtomicReference<>(Optional.empty());

    /**
     * Created by name when Citizens is enabled.
     */
    public CitizensHook() {}

    @Override
    public String pluginName() {
        return NAME;
    }

    @Override
    public void enable(Plugin plugin, NpcClickHandler handler) {
        var clicks = new Clicks(handler);
        plugin.getServer().getPluginManager().registerEvents(clicks, plugin);
        listener.getAndSet(Optional.of(clicks)).ifPresent(HandlerList::unregisterAll);
    }

    @Override
    public void disable() {
        listener.getAndSet(Optional.empty()).ifPresent(HandlerList::unregisterAll);
    }

    @Override
    public Optional<String> resolve(CommandSender sender, String argument) {
        if (SELECTED.equalsIgnoreCase(argument)) {
            return Optional.ofNullable(CitizensAPI.getDefaultNPCSelector().getSelected(sender))
                    .map(CitizensHook::key);
        }
        try {
            return Optional.ofNullable(CitizensAPI.getNPCRegistry().getById(Integer.parseInt(argument)))
                    .map(CitizensHook::key);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<String> suggestions() {
        var ids = StreamSupport.stream(CitizensAPI.getNPCRegistry().spliterator(), false)
                .map(CitizensHook::key)
                .limit(MAX_SUGGESTIONS);
        return Stream.concat(Stream.of(SELECTED), ids).toList();
    }

    private static String key(NPC npc) {
        return String.valueOf(npc.getId());
    }

    /**
     * Reports right-clicks on NPCs of the main registry.
     *
     * @param handler told about the clicks
     */
    public record Clicks(NpcClickHandler handler) implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onRightClick(NPCRightClickEvent event) {
            var npc = event.getNPC();
            if (npc.getOwningRegistry() != CitizensAPI.getNPCRegistry()) return;
            handler.clicked(event.getClicker(), NAME, key(npc));
        }
    }
}
