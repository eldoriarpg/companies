/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.data.repository.AOrderData;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RefreshService implements Runnable {
    private final AOrderData orderData;

    private RefreshService(AOrderData orderData) {
        this.orderData = orderData;
    }

    public static RefreshService create(AOrderData orderData, ScheduledExecutorService executorService) {
        var refreshService = new RefreshService(orderData);
        executorService.scheduleAtFixedRate(refreshService, 5L, 60L, TimeUnit.MINUTES);
        return refreshService;
    }

    @Override
    public void run() {
        orderData.refreshMaterialPrices();
        orderData.invalidateMaterialPriceCache();
    }
}
