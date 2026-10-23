/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.Companies;
import de.eldoria.companies.data.repository.AOrderData;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Recalculates the market prices of materials every hour on the primary and drops the cached prices.
 */
public class RefreshService implements Runnable {
    private final AOrderData orderData;
    private final Runnable refreshed;

    private RefreshService(AOrderData orderData, Runnable refreshed) {
        this.orderData = orderData;
        this.refreshed = refreshed;
    }

    /**
     * Starts the hourly refresh, the first after five minutes.
     *
     * @param orderData       order repository holding the prices
     * @param executorService runs the refresh
     * @param refreshed       runs after every refresh, to tell the other nodes
     */
    public static void start(AOrderData orderData, ScheduledExecutorService executorService, Runnable refreshed) {
        var refreshService = new RefreshService(orderData, refreshed);
        executorService.scheduleAtFixedRate(refreshService, 5L, 60L, TimeUnit.MINUTES);
    }

    @Override
    public void run() {
        try {
            orderData.refreshMaterialPrices();
            orderData.invalidateMaterialPriceCache();
            refreshed.run();
        } catch (RuntimeException e) {
            Companies.logger().log(Level.SEVERE, "Could not refresh material prices", e);
        }
    }
}
