package de.eldoria.companies.services;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.events.order.OrderExpiredEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExpiringService implements Runnable, Listener {
    private final Plugin plugin;
    private final AOrderData orderData;
    private final ACompanyData companyData;
    private final Configuration configuration;

    private ExpiringService(Plugin plugin, AOrderData orderData, ACompanyData companyData, Configuration configuration) {
        this.plugin = plugin;
        this.orderData = orderData;
        this.companyData = companyData;
        this.configuration = configuration;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        // TODO: Notify players on login about expiring orders
    }

    public static ExpiringService create(Plugin plugin, AOrderData orderData, ACompanyData companyData, Configuration configuration, ScheduledExecutorService executorService) {
        var expiringService = new ExpiringService(plugin, orderData, companyData, configuration);
        var interval = configuration.generalSettings().orderCheckInterval();
        executorService.scheduleAtFixedRate(expiringService, 10L, interval, TimeUnit.MINUTES);
        return expiringService;
    }

    @Override
    public void run() {
        var expired = orderData.retrieveExpiredOrders(configuration.companySettings().deliveryHours()).join();

        for (var order : expired) {
            orderData.submitUnclaimOrder(order);
            var profile = companyData.retrieveCompanyById(order.company()).asFuture()
                    .thenApply(r -> r.map(p -> companyData.retrieveCompanyProfile(p).join().orElse(null))).join();
            if(profile.isEmpty()) continue;
            plugin.getServer().getPluginManager().callEvent(new OrderExpiredEvent(order, profile.get()));
        }
    }
}
