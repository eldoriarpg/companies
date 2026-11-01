/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.system.SystemFunding;
import de.eldoria.companies.util.Threading;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Gives money of an order back: to its owner, or for an order of the server to the account that paid it. An order
 * created without charging its owner refunds nothing.
 */
@FunctionalInterface
public interface OrderRefunds {
    /**
     * @param order  the order
     * @param amount amount to give back
     */
    void refund(SimpleOrder order, double amount);

    /**
     * @param owners deposits to an owner
     * @param system gives money of system orders back to their accounts
     * @return refunds routed by the order's owner
     */
    static OrderRefunds of(BiConsumer<UUID, Double> owners, SystemFunding system) {
        return (order, amount) -> {
            if (!order.charged()) return;
            if (SystemOwner.is(order.owner())) system.refund(order.id(), amount);
            else owners.accept(order.owner(), amount);
        };
    }

    /**
     * Refunds of a running plugin: owners are paid through Vault on the main thread.
     *
     * @param plugin  the plugin
     * @param economy the economy
     * @param system  gives money of system orders back to their accounts
     * @return the refunds
     */
    static OrderRefunds of(Plugin plugin, Economy economy, SystemFunding system) {
        var mainThread = Threading.mainThread(plugin);
        return of(
                (owner, amount) -> mainThread.execute(
                        () -> economy.depositPlayer(plugin.getServer().getOfflinePlayer(owner), amount)),
                system);
    }
}
