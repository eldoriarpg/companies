/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.orders.EconomyAccounts;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Moves the money of system orders between their economy accounts and the orders, on the main thread. Minted money
 * comes from nowhere and goes back nowhere.
 */
public final class SystemFunding implements SystemOrderService.Funding {
    private final EconomyAccounts accounts;
    private final AOrderData orderData;
    private final Executor mainThread;
    private final Logger logger;

    /**
     * @param accounts   the economy accounts
     * @param orderData  order repository, holding which account paid an order
     * @param mainThread runs the economy calls
     * @param logger     reports deposits the economy refused
     */
    public SystemFunding(EconomyAccounts accounts, AOrderData orderData, Executor mainThread, Logger logger) {
        this.accounts = accounts;
        this.orderData = orderData;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    /**
     * Withdraws on the main thread and waits for it, so call it off the main thread.
     */
    @Override
    public boolean withdraw(String account, double amount) {
        return CompletableFuture.supplyAsync(() -> accounts.withdraw(account, amount), mainThread)
                .join();
    }

    @Override
    public void deposit(String account, double amount) {
        if (!(amount > 0)) return;
        mainThread.execute(() -> {
            var response = accounts.deposit(account, amount);
            if (!response.transactionSuccess()) {
                logger.warning("Could not give %s back to the account %s: %s"
                        .formatted(amount, account, response.errorMessage));
            }
        });
    }

    /**
     * Gives money of a system order back to the account that paid it. Nothing happens for minted money or an order
     * without a stored decision.
     *
     * @param order  id of the order
     * @param amount amount to give back
     */
    public void refund(int order, double amount) {
        if (!(amount > 0)) return;
        orderData
                .systemDecision(order)
                .flatMap(SystemDecision::paidFrom)
                .ifPresent(account -> deposit(account, amount));
    }
}
