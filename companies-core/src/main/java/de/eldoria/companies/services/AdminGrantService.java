/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Grants a player money or items, as `/companyadmin grant` does: money is deposited at once, items are stored for the
 * player to collect with `/order collect`, and the player is told either way. The commands and the dashboard's admin
 * tools share it; the callers write the admin log.
 */
public final class AdminGrantService {
    private final Bank bank;
    private final ItemReturnService returns;
    private final ProfileService.Notifier notifier;

    /**
     * @param bank     deposits money
     * @param returns  stores granted items
     * @param notifier tells the player
     */
    public AdminGrantService(Bank bank, ItemReturnService returns, ProfileService.Notifier notifier) {
        this.bank = bank;
        this.returns = returns;
        this.notifier = notifier;
    }

    /**
     * Deposits money for a player. Has to be called on the main thread.
     *
     * @param target the player
     * @param amount the amount, from 0.01
     * @return the amount as the economy writes it, or why it was not deposited
     */
    public ActionResult<String> money(OfflinePlayer target, double amount) {
        var error = bank.deposit(target, amount);
        if (error != null) {
            return ActionResult.refused(
                    CommandFailure.of("companyadmin.grant.error.failed", Replacement.create("reason", error)));
        }
        var formatted = bank.format(amount);
        notifier.send(
                target.getUniqueId(),
                NotificationType.MONEY_GRANTED,
                NotificationArgs.create().money("amount", formatted));
        return ActionResult.done(formatted);
    }

    /**
     * Stores items for a player to collect.
     *
     * @param target the player
     * @param stack  the item, one of it
     * @param amount how many
     * @return true when they were stored
     */
    public CompletableFuture<Boolean> items(UUID target, ItemStack stack, int amount) {
        return returns.store(
                        target, ReturnKind.OWNER, stack.asOne(), amount, ItemsReturnedEvent.Reason.ADMIN_GRANT, null)
                .thenApply(stored -> {
                    if (!Boolean.TRUE.equals(stored)) return false;
                    notifier.send(
                            target,
                            NotificationType.ITEMS_GRANTED,
                            NotificationArgs.create().number("amount", amount));
                    return true;
                });
    }

    /**
     * Where granted money comes from.
     */
    public interface Bank {
        /**
         * Deposits money for a player.
         *
         * @param player the player
         * @param amount the amount
         * @return why it failed, none when it was deposited
         */
        @Nullable
        String deposit(OfflinePlayer player, double amount);

        /**
         * @param amount an amount
         * @return the amount as the economy writes it
         */
        String format(double amount);

        /**
         * @param economy the server's economy
         * @return a bank depositing through it
         */
        static Bank of(Economy economy) {
            return new Bank() {
                @Override
                public @Nullable String deposit(OfflinePlayer player, double amount) {
                    var response = economy.depositPlayer(player, amount);
                    return response.transactionSuccess() ? null : String.valueOf(response.errorMessage);
                }

                @Override
                public String format(double amount) {
                    return economy.format(amount);
                }
            };
        }
    }
}
