package de.eldoria.companies.services;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyLevelDownEvent;
import de.eldoria.companies.events.company.CompanyLevelUpEvent;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.OrderExpiredEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.logging.Level;

public class LevelService implements Listener {
    private final Plugin plugin;
    private final Configuration configuration;
    private final ACompanyData companyData;

    public LevelService(Plugin plugin, Configuration configuration, ACompanyData companyData) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.companyData = companyData;
    }

    @EventHandler
    public void onOrderCanceled(OrderCanceledEvent event) {
        companyData.submitFailedOrder(event.company(), configuration.companySettings().abortedOrderPenalty())
                .asFuture()
                .exceptionally(err -> {
                    plugin.getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                })
                .thenRun(() -> updateCompanyLevel(event.company()));
    }

    @EventHandler
    public void onOrderExpired(OrderExpiredEvent event) {
        companyData.submitFailedOrder(event.company(), configuration.companySettings().expiredOrderPenalty())
                .asFuture()
                .exceptionally(err -> {
                    plugin.getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                })
                .thenRun(() -> updateCompanyLevel(event.company()));
    }

    @EventHandler
    public void onOrderDone(OrderDoneEvent event) {
        updateCompanyLevel(event.company());
    }

    public void updateCompanyLevel(CompanyProfile company) {
        companyData.retrieveCompanyStats(company)
                .whenComplete(stats -> {
                    var companySettings = configuration.companySettings();
                    var newLevel = companySettings.calcCompanyLevel(stats);
                    if (newLevel.level() == company.level()) return;
                    var oldLevel = companySettings.level(company.level());
                    if (oldLevel.isEmpty()) return;
                    companyData.submitCompanyLevelUpdate(company, newLevel.level());
                    if (newLevel.level() > company.level()) {
                        plugin.getServer().getPluginManager().callEvent(new CompanyLevelUpEvent(company, oldLevel.get(), newLevel));
                        return;
                    }
                    plugin.getServer().getPluginManager().callEvent(new CompanyLevelDownEvent(company, oldLevel.get(), newLevel));
                });
    }
}
