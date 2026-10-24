/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.player;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.KnownPlayers;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.menus.admin.AdminOrderActions;
import de.eldoria.companies.menus.admin.AdminPlayerMenu;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.util.Threading;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.permission.Permission;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /companyadmin player <player>}: the company and role of a player, the player's orders, the items waiting and
 * the latest notifications. The player may be offline if the server knows them.
 */
@Subcommand("player")
@Permission(Admin.PLAYER)
@PlayerOnly
public final class AdminPlayerCommand {
    /**
     * Notifications listed.
     */
    static final int LATEST = 5;

    private final CommandDependencies deps;
    private final AdminOrderActions orders;

    /**
     * @param deps   what the commands need
     * @param orders opens the orders listed
     */
    public AdminPlayerCommand(CommandDependencies deps, AdminOrderActions orders) {
        this.deps = deps;
        this.orders = orders;
    }

    /**
     * @param sender sender of the command
     * @param player name of the player
     */
    @Executes
    public void player(CommandSender sender, @KnownPlayers String player) {
        var admin = Senders.player(sender);
        var services = deps.services();
        var target = deps.plugin().getServer().getOfflinePlayerIfCached(player);
        if (target == null) throw CommandFailure.of("error.unknownPlayer");
        deps.adminLog()
                .entry(admin, "player")
                .with("player", target.getName())
                .with("uuid", target.getUniqueId())
                .write();
        var menu = new AdminPlayerMenu(
                deps.messages(), services.notificationService().renderer(), orders);
        CompletableFuture.supplyAsync(() -> model(target), Threading.VIRTUAL)
                .thenAccept(model -> services.menus().show(admin, menu, model))
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not look up a player", err);
                    return null;
                });
    }

    private AdminPlayerMenu.Model model(OfflinePlayer target) {
        var id = target.getUniqueId();
        var orderData = deps.orderData();
        var inbox = deps.information().inbox();
        var profile = deps.companyData().retrievePlayerCompanyProfile(target).join();
        var owned = orderData
                .retrieveOrdersByPlayer(target, OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join();
        var workerItems =
                orderData.retrieveItemReturnCount(id, ReturnKind.WORKER).join();
        var ownerItems = orderData.retrieveItemReturnCount(id, ReturnKind.OWNER).join();
        var unread = inbox.retrieveUnreadCount(id, 0L).join();
        var latest = inbox.retrievePage(id, 1, LATEST).join();
        return new AdminPlayerMenu.Model(
                String.valueOf(target.getName()),
                id,
                membership(profile, target),
                owned == null ? List.of() : owned,
                workerItems == null ? 0 : workerItems,
                ownerItems == null ? 0 : ownerItems,
                unread,
                latest);
    }

    private static Optional<AdminPlayerMenu.Membership> membership(
            Optional<CompanyProfile> profile, OfflinePlayer target) {
        if (profile == null) return Optional.empty();
        return profile.flatMap(company -> company.member(target)
                .map(member -> new AdminPlayerMenu.Membership(company.name(), member.isOwner(), member.permissions())));
    }
}
