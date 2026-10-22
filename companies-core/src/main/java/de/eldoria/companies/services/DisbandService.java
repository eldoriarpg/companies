/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.util.Threading;
import org.bukkit.plugin.Plugin;

/**
 * Dissolves companies.
 * <p>
 * Orders the company worked on are open again: workers get their delivered items back and owners are told, without
 * naming the company.
 */
public class DisbandService {
    private final Plugin plugin;
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final ItemReturnService itemReturnService;
    private final NotificationService notificationService;

    public DisbandService(
            Plugin plugin,
            ACompanyData companyData,
            AOrderData orderData,
            ItemReturnService itemReturnService,
            NotificationService notificationService) {
        this.plugin = plugin;
        this.companyData = companyData;
        this.orderData = orderData;
        this.itemReturnService = itemReturnService;
        this.notificationService = notificationService;
    }

    /**
     * Releases the company's orders and deletes the company. Blocks, so it must not run on the main thread.
     *
     * @param company company to dissolve
     * @return true if the company was deleted
     */
    public boolean disband(CompanyProfile company) {
        var released = orderData.submitCompanyOrdersPurge(company).join();
        if (released != null) {
            itemReturnService.notifyWorkers(released.workers());
            released.orders().forEach(notificationService::notifyOrderOpen);
        }
        if (!Boolean.TRUE.equals(companyData.submitCompanyPurge(company).join())) return false;
        Threading.mainThread(plugin)
                .execute(() -> plugin.getServer().getPluginManager().callEvent(new CompanyDisbandEvent(company)));
        return true;
    }
}
