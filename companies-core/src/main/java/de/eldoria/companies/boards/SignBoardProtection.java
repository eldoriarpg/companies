/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.util.Permission;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Protects board signs: nobody edits them, only admins with {@link Permission.Admin#BOARDS} break them, and
 * explosions and fire leave them alone.
 * <p>
 * A board sign falls off when the block it hangs on is removed, and pistons move it like any block; board signs belong
 * on blocks protected by a region plugin.
 */
public final class SignBoardProtection implements Listener {
    private final BoardSignData data;
    private final SignBoardIndex index;
    private final Consumer<BoardLocation> removed;
    private final BiConsumer<CommandSender, CommandFailure> report;

    /**
     * @param data    reads the board of a sign
     * @param index   the board signs of loaded chunks
     * @param removed told about a board sign an admin broke
     * @param report  shows a failure to a player
     */
    public SignBoardProtection(
            BoardSignData data,
            SignBoardIndex index,
            Consumer<BoardLocation> removed,
            BiConsumer<CommandSender, CommandFailure> report) {
        this.data = data;
        this.index = index;
        this.removed = removed;
        this.report = report;
    }

    /**
     * @param event a player opening the sign editor
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpenSign(PlayerOpenSignEvent event) {
        if (data.stored(event.getSign()).isPresent()) event.setCancelled(true);
    }

    /**
     * @param event a player breaking a block
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        var block = event.getBlock();
        if (!data.isBoard(block)) return;
        var player = event.getPlayer();
        if (!player.hasPermission(Permission.Admin.BOARDS)) {
            event.setCancelled(true);
            report.accept(player, CommandFailure.actionBar("boards.error.protected"));
            return;
        }
        var location = BoardLocation.of(block);
        index.remove(location);
        removed.accept(location);
    }

    /**
     * @param event a block exploding, like a bed in the nether
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(data::isBoard);
    }

    /**
     * @param event an entity exploding, like a creeper
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(data::isBoard);
    }

    /**
     * @param event a block burning away
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (data.isBoard(event.getBlock())) event.setCancelled(true);
    }
}
