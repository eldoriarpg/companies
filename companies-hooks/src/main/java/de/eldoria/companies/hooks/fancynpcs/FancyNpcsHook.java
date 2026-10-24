/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.fancynpcs;

import de.eldoria.companies.boards.npc.NpcClickHandler;
import de.eldoria.companies.boards.npc.NpcHook;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.actions.ActionTrigger;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * NPC boards on FancyNpcs NPCs, named by their name. Only right-clicks open a board.
 */
public final class FancyNpcsHook implements NpcHook {
    private static final String NAME = "FancyNpcs";
    private static final int MAX_SUGGESTIONS = 100;

    private final AtomicReference<Optional<Listener>> listener = new AtomicReference<>(Optional.empty());

    /**
     * Created by name when FancyNpcs is enabled.
     */
    public FancyNpcsHook() {}

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
        return Optional.ofNullable(FancyNpcsPlugin.get().getNpcManager().getNpc(argument))
                .map(FancyNpcsHook::key);
    }

    @Override
    public List<String> suggestions() {
        return FancyNpcsPlugin.get().getNpcManager().getAllNpcs().stream()
                .map(FancyNpcsHook::key)
                .filter(Objects::nonNull)
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    private static String key(Npc npc) {
        return npc.getData().getName();
    }

    /**
     * Reports right-clicks on NPCs.
     *
     * @param handler told about the clicks
     */
    public record Clicks(NpcClickHandler handler) implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onInteract(NpcInteractEvent event) {
            if (event.getInteractionType() != ActionTrigger.RIGHT_CLICK) return;
            handler.clicked(event.getPlayer(), NAME, key(event.getNpc()));
        }
    }
}
