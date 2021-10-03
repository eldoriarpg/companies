package de.eldoria.companies.services;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.events.company.CompanyLevelDownEvent;
import de.eldoria.companies.events.company.CompanyLevelUpEvent;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.OrderExpiredEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

public class LevelService implements Listener {
    private final Plugin plugin;
    private final Configuration configuration;
    private final ACompanyData companyData;
    private final ExecutorService executorService;

    public LevelService(Plugin plugin, Configuration configuration, ACompanyData companyData, ExecutorService executorService) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.companyData = companyData;
        this.executorService = executorService;
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

    public void updateCompanyLevel(ICompanyProfile company) {
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

    public void updateAllCompanies(Runnable onComplete) {
        companyData.getCompanies()
                .thenAccept(companies -> {
                    for (var company : companies) {
                        var join = companyData.retrieveCompanyProfile(company).join();
                        if(join == null || join.isEmpty()) continue;
                        updateCompanyLevel(join.get());
                    }
                }).thenRun(onComplete);
    }
}
