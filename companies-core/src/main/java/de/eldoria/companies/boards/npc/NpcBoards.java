/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards.npc;

import de.eldoria.companies.boards.Boards;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.BoardBindings;
import de.eldoria.companies.configuration.elements.boardbindings.NpcBinding;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * NPCs opening boards: a right-click on a bound NPC opens its board, through {@link Boards#open} with its permission
 * and rate limit, like a board sign.
 * <p>
 * The bindings live in {@code boards.yml} of this node. Until the hooks are {@link #start started}, and for NPC plugins
 * that are not installed, no hook is known and nothing can be bound.
 */
public final class NpcBoards implements NpcClickHandler {
    private final Plugin plugin;
    private final Boards boards;
    private final Supplier<BoardBindings> bindings;
    private final Runnable save;
    private final BiConsumer<CommandSender, CommandFailure> report;
    private final AtomicReference<Optional<NpcHooks>> hooks = new AtomicReference<>(Optional.empty());

    /**
     * @param plugin   the plugin
     * @param boards   resolves and opens boards
     * @param bindings the current bindings
     * @param save     writes the bindings to their file
     * @param report   shows a failure to a player
     */
    public NpcBoards(
            Plugin plugin,
            Boards boards,
            Supplier<BoardBindings> bindings,
            Runnable save,
            BiConsumer<CommandSender, CommandFailure> report) {
        this.plugin = plugin;
        this.boards = boards;
        this.bindings = bindings;
        this.save = save;
        this.report = report;
    }

    /**
     * Loads the hooks of the available NPC plugins, which report clicks on NPCs from now on.
     *
     * @param npcHooks the hooks to load
     */
    public void start(NpcHooks npcHooks) {
        npcHooks.load(plugin, this);
        hooks.getAndSet(Optional.of(npcHooks)).ifPresent(NpcHooks::unload);
    }

    /**
     * Unloads the hooks.
     */
    public void stop() {
        hooks.getAndSet(Optional.empty()).ifPresent(NpcHooks::unload);
    }

    /**
     * @param name name of an NPC plugin, ignoring case
     * @return its loaded hook
     */
    public Optional<NpcHook> hook(String name) {
        return hooks.get().flatMap(loaded -> loaded.hook(name));
    }

    /**
     * @return the names of the loaded hooks
     */
    public List<String> hookNames() {
        return hooks.get().map(NpcHooks::names).orElse(List.of());
    }

    /**
     * Opens the board of a bound NPC on the main thread. Clicks on other NPCs are ignored.
     *
     * @param player the player who clicked
     * @param hook   name of the NPC plugin
     * @param npcKey key of the NPC in that plugin
     */
    @Override
    public void clicked(Player player, String hook, String npcKey) {
        if (!plugin.getServer().isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> clicked(player, hook, npcKey));
            return;
        }
        bindings.get().board(hook, npcKey).ifPresent(board -> open(player, board));
    }

    private void open(Player player, String board) {
        try {
            boards.open(player, boards.require(board));
        } catch (CommandFailure failure) {
            report.accept(player, failure);
        }
    }

    /**
     * Binds an NPC to a board and saves the bindings.
     *
     * @param sender the admin
     * @param hook   name of the NPC plugin, ignoring case
     * @param npc    the NPC as typed, resolved by its hook
     * @param board  preset name or materials separated by spaces, empty for every open order
     * @return the binding
     * @throws CommandFailure when the hook is not loaded, the NPC does not exist or the board is unknown
     */
    public NpcBinding link(CommandSender sender, String hook, String npc, String board) {
        var npcHook = requireHook(hook);
        var key = npcHook.resolve(sender, npc).orElseThrow(() -> unknownNpc(npcHook.pluginName(), npc));
        var target = boards.require(board);
        var binding = new NpcBinding(npcHook.pluginName(), key, target.spec());
        bindings.get().link(binding.hook(), binding.npc(), binding.board());
        save.run();
        return binding;
    }

    /**
     * Removes the board of an NPC and saves the bindings. An NPC that no longer exists is unbound by its stored key.
     *
     * @param sender the admin
     * @param hook   name of the NPC plugin, ignoring case
     * @param npc    the NPC as typed
     * @return the NPC's binding before it was removed, empty when it was not bound
     */
    public Optional<NpcBinding> unlink(CommandSender sender, String hook, String npc) {
        var key = hook(hook).flatMap(npcHook -> npcHook.resolve(sender, npc)).orElse(npc);
        var current = bindings.get();
        var binding =
                current.npcs().stream().filter(entry -> entry.isFor(hook, key)).findFirst();
        if (binding.isEmpty()) return Optional.empty();
        current.unlink(hook, key);
        save.run();
        return binding;
    }

    /**
     * @return the bound NPCs
     */
    public List<NpcBinding> bindings() {
        return bindings.get().npcs();
    }

    private NpcHook requireHook(String hook) {
        return hook(hook)
                .orElseThrow(() -> CommandFailure.of("boards.error.unknownHook", Placeholder.unparsed("hook", hook)));
    }

    private static CommandFailure unknownNpc(String hook, String npc) {
        return CommandFailure.of(
                "boards.error.unknownNpc", Placeholder.unparsed("hook", hook), Placeholder.unparsed("npc", npc));
    }
}
