/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.board;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.BoardPresets;
import de.eldoria.companies.commands.suggest.NpcHookNames;
import de.eldoria.companies.commands.suggest.NpcKeys;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.boardbindings.NpcBinding;
import de.eldoria.eldoutilities.localization.ILocalizer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;

/**
 * {@code /companyadmin board npc}: NPCs of Citizens or FancyNpcs that open a board when right-clicked.
 * <ul>
 *     <li>{@code link <hook> <npc> [board]} binds an NPC to a preset, to materials or, without a board, to every open
 *     order. A Citizens NPC is named by its id or {@code selected}, a FancyNpcs NPC by its name;</li>
 *     <li>{@code unlink <hook> <npc>} removes the board of an NPC;</li>
 *     <li>{@code list} shows the bound NPCs.</li>
 * </ul>
 * Other NPC plugins open boards by running {@code /company board [board]} as the player.
 */
@Subcommand("npc")
public final class NpcBoardCommand {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public NpcBoardCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Binds an NPC to every open order.
     *
     * @param sender sender of the command
     * @param hook   name of the NPC plugin
     * @param npc    the NPC
     */
    @Executes("link")
    public void link(CommandSender sender, @NpcHookNames String hook, @NpcKeys String npc) {
        link(sender, hook, npc, "");
    }

    /**
     * Binds an NPC to a preset or materials.
     *
     * @param sender sender of the command
     * @param hook   name of the NPC plugin
     * @param npc    the NPC
     * @param board  preset name or materials separated by spaces
     */
    @Executes("link")
    public void link(
            CommandSender sender,
            @NpcHookNames String hook,
            @NpcKeys String npc,
            @BoardPresets @StringArg(StringArgType.GREEDY) String board) {
        var binding = deps.npcBoards().link(sender, hook, npc, board);
        deps.adminLog()
                .entry(sender, "board npc link")
                .with("hook", binding.hook())
                .with("npc", binding.npc())
                .with("board", binding.board())
                .write();
        deps.messages().sendMessage(sender, "companyadmin.board.npc.linked", values(binding));
    }

    /**
     * Removes the board of an NPC.
     *
     * @param sender sender of the command
     * @param hook   name of the NPC plugin
     * @param npc    the NPC
     */
    @Executes("unlink")
    public void unlink(CommandSender sender, @NpcHookNames String hook, @NpcKeys String npc) {
        var binding = deps.npcBoards()
                .unlink(sender, hook, npc)
                .orElseThrow(() -> CommandFailure.of(
                        "boards.error.notLinked",
                        Placeholder.unparsed("hook", hook),
                        Placeholder.unparsed("npc", npc)));
        deps.adminLog()
                .entry(sender, "board npc unlink")
                .with("hook", binding.hook())
                .with("npc", binding.npc())
                .write();
        deps.messages().sendMessage(sender, "companyadmin.board.npc.unlinked", values(binding));
    }

    /**
     * Lists the bound NPCs.
     *
     * @param sender sender of the command
     */
    @Executes("list")
    public void list(CommandSender sender) {
        var bindings = deps.npcBoards().bindings();
        var messages = deps.messages();
        if (bindings.isEmpty()) {
            messages.sendMessage(sender, "companyadmin.board.npc.none");
            return;
        }
        messages.sendMessage(sender, "companyadmin.board.npc.list");
        bindings.forEach(binding -> messages.sendMessage(sender, "companyadmin.board.npc.entry", values(binding)));
    }

    private TagResolver[] values(NpcBinding binding) {
        return new TagResolver[] {
            Placeholder.unparsed("hook", binding.hook()),
            Placeholder.unparsed("npc", binding.npc()),
            Placeholder.unparsed("board", boardName(binding.board()))
        };
    }

    private String boardName(String board) {
        if (!board.isBlank()) return board;
        return ILocalizer.getPluginLocalizer(deps.plugin()).localize("boards.sign.all");
    }
}
