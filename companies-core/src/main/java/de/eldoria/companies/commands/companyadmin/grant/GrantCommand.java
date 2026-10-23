/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.grant;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.KnownPlayers;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.eldoutilities.messages.Replacement;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.DoubleArg;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.permission.Permission;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * {@code /companyadmin grant}: gives a player items or money.
 * <ul>
 *     <li>{@code items <player> [amount]} stores the item in the admin's main hand for the player, who collects it
 *     with {@code /order collect}; the amount defaults to the held stack;</li>
 *     <li>{@code money <player> <amount>} deposits money.</li>
 * </ul>
 * The player is notified and the grant is written to the server log.
 */
@Subcommand("grant")
@Permission(Admin.GRANT)
@PlayerOnly
public final class GrantCommand {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public GrantCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Grants the held stack.
     *
     * @param sender sender of the command
     * @param player name of the player
     */
    @Executes("items")
    public void items(CommandSender sender, @KnownPlayers String player) {
        var admin = Senders.player(sender);
        items(admin, player, admin.getInventory().getItemInMainHand().getAmount());
    }

    /**
     * Grants an amount of the held item.
     *
     * @param sender sender of the command
     * @param player name of the player
     * @param amount amount of items
     */
    @Executes("items")
    public void items(CommandSender sender, @KnownPlayers String player, @IntArg(min = 1, max = 100_000) int amount) {
        items(Senders.player(sender), player, amount);
    }

    /**
     * Deposits money.
     *
     * @param sender sender of the command
     * @param player name of the player
     * @param amount amount of money
     */
    @Executes("money")
    public void money(
            CommandSender sender, @KnownPlayers String player, @DoubleArg(min = 0.01, max = 1.0E12) double amount) {
        var admin = Senders.player(sender);
        var economy = deps.services().economy();
        var target = target(player);
        var response = economy.depositPlayer(target, amount);
        if (!response.transactionSuccess()) {
            throw CommandFailure.of(
                    "companyadmin.grant.error.failed",
                    Replacement.create("reason", String.valueOf(response.errorMessage)));
        }
        var formatted = economy.format(amount);
        deps.adminLog()
                .entry(admin, "grant money")
                .with("player", target.getName())
                .with("uuid", target.getUniqueId())
                .with("amount", amount)
                .write();
        deps.services()
                .notificationService()
                .send(
                        target.getUniqueId(),
                        NotificationType.MONEY_GRANTED,
                        NotificationArgs.create().money("amount", formatted));
        deps.messages()
                .sendMessage(
                        admin,
                        "companyadmin.grant.money",
                        Replacement.create("amount", formatted),
                        Replacement.create("player_name", String.valueOf(target.getName())));
    }

    private void items(Player admin, String player, int amount) {
        var services = deps.services();
        var held = admin.getInventory().getItemInMainHand();
        if (held.isEmpty()) throw CommandFailure.of("companyadmin.grant.error.emptyHand");
        var target = target(player);
        var stack = held.asOne();
        deps.orderData()
                .submitItemReturn(target.getUniqueId(), ReturnKind.OWNER, stack, amount)
                .asFuture()
                .thenAccept(stored -> {
                    if (!Boolean.TRUE.equals(stored)) {
                        deps.messages().sendError(admin, "error.saveFailed");
                        return;
                    }
                    deps.adminLog()
                            .entry(admin, "grant items")
                            .with("player", target.getName())
                            .with("uuid", target.getUniqueId())
                            .with("item", stack.getType().getKey())
                            .with("amount", amount)
                            .write();
                    services.notificationService()
                            .send(
                                    target.getUniqueId(),
                                    NotificationType.ITEMS_GRANTED,
                                    NotificationArgs.create().number("amount", amount));
                    deps.messages()
                            .sendMessage(
                                    admin,
                                    "companyadmin.grant.items",
                                    Replacement.create("amount", amount),
                                    Replacement.create("player_name", String.valueOf(target.getName())));
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not grant items", err);
                    return null;
                });
    }

    private OfflinePlayer target(String name) {
        var target = deps.plugin().getServer().getOfflinePlayerIfCached(name);
        if (target == null) throw CommandFailure.of("error.unknownPlayer");
        return target;
    }
}
