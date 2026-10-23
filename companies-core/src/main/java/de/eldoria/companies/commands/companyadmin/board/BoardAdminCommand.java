/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.board;

import de.eldoria.companies.boards.BoardTarget;
import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.util.Permission.Admin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /companyadmin board}: the order boards.
 * <ul>
 *     <li>{@code presets} lists the configured presets with their open orders;</li>
 *     <li>{@code reload} writes every loaded board sign again, after the presets changed and the configuration was
 *     reloaded;</li>
 *     <li>{@code npc} binds NPCs to boards, see {@link NpcBoardCommand}.</li>
 * </ul>
 */
@Subcommand("board")
@Permission(Admin.BOARDS)
public final class BoardAdminCommand {
    private final CommandDependencies deps;

    @Subcommand
    public final NpcBoardCommand npc;

    /**
     * @param deps what the commands need
     */
    public BoardAdminCommand(CommandDependencies deps) {
        this.deps = deps;
        npc = new NpcBoardCommand(deps);
    }

    /**
     * Lists the presets with their open orders.
     *
     * @param sender sender of the command
     */
    @Executes("presets")
    public void presets(CommandSender sender) {
        var boards = deps.boards();
        Map<BoardTarget, CompletableFuture<Integer>> counts = new LinkedHashMap<>();
        for (var name : boards.presetNames()) {
            boards.resolve(name).ifPresent(target -> counts.put(target, boards.openCount(target.filter())));
        }
        if (counts.isEmpty()) {
            deps.messages().sendMessage(sender, "companyadmin.board.noPresets");
            return;
        }
        CompletableFuture.allOf(counts.values().toArray(CompletableFuture[]::new))
                .thenRun(() -> sendPresets(sender, counts))
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not count the orders of the presets.", err);
                    return null;
                });
    }

    private void sendPresets(CommandSender sender, Map<BoardTarget, CompletableFuture<Integer>> counts) {
        var messages = deps.messages();
        messages.sendMessage(sender, "companyadmin.board.presets");
        counts.forEach((target, count) -> messages.sendMessage(
                sender,
                "companyadmin.board.preset",
                Placeholder.unparsed("name", target.spec()),
                Placeholder.unparsed("label", target.label()),
                Placeholder.unparsed("count", String.valueOf(count.join()))));
    }

    /**
     * Writes every loaded board sign again.
     *
     * @param sender sender of the command
     */
    @Executes("reload")
    public void reload(CommandSender sender) {
        deps.boards().presetsChanged();
        deps.adminLog().entry(sender, "board reload").write();
        deps.messages().sendMessage(sender, "companyadmin.board.reloaded");
    }
}
