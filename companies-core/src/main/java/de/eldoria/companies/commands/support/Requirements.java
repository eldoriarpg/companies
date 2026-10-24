/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.support;

import de.eldoria.companies.util.Permission;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The custom requirements of the commands.
 */
public final class Requirements {
    private static final List<String> ADMIN_PERMISSIONS = List.of(
            Permission.Admin.ADMIN,
            Permission.Admin.CALC_LEVEL,
            Permission.Admin.DELETE,
            Permission.Admin.LEVEL,
            Permission.Admin.RELOAD,
            Permission.Admin.RENAME,
            Permission.Admin.TRANSFER_OWNER,
            Permission.Admin.ORDERS,
            Permission.Admin.PLAYER,
            Permission.Admin.GRANT,
            Permission.Admin.BOARDS,
            Permission.Admin.TREASURY,
            Permission.Admin.NODES,
            Permission.Admin.DEBUG);

    private Requirements() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param source source of the command
     * @return whether the command is sent by a player
     */
    @PlayerOnly
    public static boolean isPlayer(CommandSourceStack source) {
        return isPlayer(source.getSender());
    }

    /**
     * @param source source of the command
     * @return whether the sender may create debug reports
     */
    @DebugAccess
    public static boolean mayDebug(CommandSourceStack source) {
        return mayDebug(source.getSender());
    }

    /**
     * @param source source of the command
     * @return whether the sender may use any admin command
     */
    @AdminAccess
    public static boolean isAdmin(CommandSourceStack source) {
        return isAdmin(source.getSender());
    }

    /**
     * @param sender sender of the command
     * @return whether the sender is a player
     */
    public static boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }

    /**
     * @param sender sender of the command
     * @return whether the sender may create debug reports
     */
    public static boolean mayDebug(CommandSender sender) {
        return sender.hasPermission(Permission.Admin.DEBUG);
    }

    /**
     * @param sender sender of the command
     * @return whether the sender has any of the admin permissions, so the admin command is worth showing
     */
    public static boolean isAdmin(CommandSender sender) {
        return ADMIN_PERMISSIONS.stream().anyMatch(sender::hasPermission);
    }
}
