/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.sessions;

import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.util.Permission;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

/**
 * The Bukkit permissions of dashboard players, who may be offline while they use the dashboard.
 * <p>
 * Bukkit cannot check the permissions of an offline player. The permissions that matter to the dashboard are
 * remembered with the session when it starts and refreshed while the player is online; an offline player is checked
 * against that copy, an online player with Bukkit as in game. The highest {@code companies.orders.limit.<n>} is kept as
 * that node.
 */
public final class SessionPermissions {
    /**
     * The permissions remembered with a session.
     */
    public static final List<String> RELEVANT = List.of(
            Permission.Dashboard.USE,
            Permission.Orders.CREATE,
            Permission.Orders.REPEAT,
            Permission.Orders.SOLO,
            Permission.Company.CREATE,
            Permission.Company.JOIN,
            Permission.Boards.USE,
            Permission.Admin.ADMIN,
            Permission.Admin.BOARDS,
            Permission.Admin.CALC_LEVEL,
            Permission.Admin.DASHBOARD,
            Permission.Admin.DELETE,
            Permission.Admin.GRANT,
            Permission.Admin.LEVEL,
            Permission.Admin.NODES,
            Permission.Admin.ORDERS,
            Permission.Admin.PLAYER,
            Permission.Admin.RENAME,
            Permission.Admin.TRANSFER_OWNER,
            Permission.Admin.TREASURY);

    private final Function<UUID, Optional<Player>> online;

    /**
     * @param online finds a player online on this node; must only be called where Bukkit allows it
     */
    public SessionPermissions(Function<UUID, Optional<Player>> online) {
        this.online = online;
    }

    /**
     * The permissions of an online player that are remembered with a session.
     *
     * @param player the player
     * @return the granted permissions among {@link #RELEVANT} and the order limit node
     */
    public static Set<String> of(Player player) {
        var granted = new TreeSet<String>();
        for (var node : RELEVANT) {
            if (player.hasPermission(node)) granted.add(node);
        }
        Permission.Orders.getOrderOverride(player)
                .ifPresent(limit -> granted.add(Permission.Orders.LIMIT + "." + limit));
        return granted;
    }

    /**
     * Whether remembered permissions make the player an admin, who is shown the server's deprecation notices.
     *
     * @param permissions the remembered permissions
     * @return true with {@code companies.admin} or any {@code companies.admin.*}
     */
    public static boolean admin(Set<String> permissions) {
        return permissions.stream()
                .anyMatch(node -> node.equals(Permission.Admin.ADMIN) || node.startsWith(Permission.Admin.ADMIN + "."));
    }

    /**
     * Whether the player of a session has a permission: checked with Bukkit while online here, otherwise against the
     * remembered copy.
     *
     * @param session the session
     * @param node    the permission
     * @return true when granted
     */
    public boolean has(StoredSession session, String node) {
        return online.apply(session.player())
                .map(player -> player.hasPermission(node))
                .orElseGet(() -> session.permissions().contains(node));
    }
}
