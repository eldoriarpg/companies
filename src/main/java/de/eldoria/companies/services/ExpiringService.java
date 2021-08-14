package de.eldoria.companies.services;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExpiringService implements Runnable {
    private final AOrderData orderData;
    private final Configuration configuration;

    private ExpiringService(AOrderData orderData, Configuration configuration) {
        this.orderData = orderData;
        this.configuration = configuration;
    }

    public static ExpiringService create(AOrderData orderData, Configuration configuration, ScheduledExecutorService executorService) {
        var expiringService = new ExpiringService(orderData, configuration);
        var interval = configuration.generalSettings().orderCheckInterval();
        executorService.scheduleAtFixedRate(expiringService, 10L, interval, TimeUnit.MINUTES);
        return expiringService;
    }

    @Override
    public void run() {
        var expired = orderData.retrieveExpiredOrders(configuration.companySettings().deliveryHours()).join();

        for (var order : expired) {
            orderData.submitUnclaimOrder(order);
        }
    }
}
