/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.util.Threading;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Hands collected fees and taxes to the configured collector account.
 * <p>
 * Without a collector account the money is destroyed. The account is named as {@link EconomyAccounts} reads it.
 */
public class Collector {
    private final Plugin plugin;
    private final EconomyAccounts accounts;
    private final Configuration configuration;

    public Collector(Plugin plugin, Economy economy, Configuration configuration) {
        this.plugin = plugin;
        accounts = new EconomyAccounts(plugin.getServer(), economy);
        this.configuration = configuration;
    }

    /**
     * Deposits an amount to the collector account on the main thread. Nothing happens for amounts of zero or less or
     * without a collector account.
     *
     * @param amount amount to collect
     */
    public void collect(double amount) {
        if (!(amount > 0)) return;
        var account = configuration.economySettings().collectorAccount();
        if (account.isEmpty()) return;
        Threading.mainThread(plugin).execute(() -> {
            var response = accounts.deposit(account, amount);
            if (!response.transactionSuccess()) {
                plugin.getLogger()
                        .log(
                                Level.WARNING,
                                "Could not deposit %s to the collector account %s: %s"
                                        .formatted(amount, account, response.errorMessage));
            }
        });
    }
}
