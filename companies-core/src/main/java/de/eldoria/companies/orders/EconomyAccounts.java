/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Server;

import java.util.Optional;
import java.util.UUID;

/**
 * Economy accounts named in the configuration. An account that is a UUID is the player with that id, anything else is
 * passed to Vault as an account name, which economy plugins use for accounts that are not players. Call it on the main
 * thread, as Vault expects.
 */
public final class EconomyAccounts {
    private final Server server;
    private final Economy economy;

    /**
     * @param server  the server, to look players up
     * @param economy the economy holding the accounts
     */
    public EconomyAccounts(Server server, Economy economy) {
        this.server = server;
        this.economy = economy;
    }

    /**
     * @param account the account
     * @param amount  amount to deposit
     * @return what the economy answered
     */
    @SuppressWarnings("deprecation")
    public EconomyResponse deposit(String account, double amount) {
        return playerId(account)
                .map(id -> economy.depositPlayer(server.getOfflinePlayer(id), amount))
                .orElseGet(() -> economy.depositPlayer(account, amount));
    }

    /**
     * Withdraws an amount when the account holds it.
     *
     * @param account the account
     * @param amount  amount to withdraw
     * @return true when it was withdrawn
     */
    @SuppressWarnings("deprecation")
    public boolean withdraw(String account, double amount) {
        var id = playerId(account);
        if (id.isPresent()) {
            var player = server.getOfflinePlayer(id.get());
            return economy.has(player, amount)
                    && economy.withdrawPlayer(player, amount).transactionSuccess();
        }
        return economy.has(account, amount)
                && economy.withdrawPlayer(account, amount).transactionSuccess();
    }

    private static Optional<UUID> playerId(String account) {
        try {
            return Optional.of(UUID.fromString(account));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
