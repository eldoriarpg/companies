/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.util.AsyncEvents;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Dissolves companies.
 * <p>
 * Orders the company worked on are open again: workers get their delivered items back and owners are told, without
 * naming the company. The treasury is emptied before the company is deleted and its balance is paid to the owner once
 * the company is gone; when the company cannot be deleted, the balance goes back into the treasury.
 */
public class DisbandService {
    private final Plugin plugin;
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final ItemReturnService itemReturnService;
    private final NotificationService notificationService;
    private final TreasuryService treasuryService;

    /**
     * @param plugin              the plugin
     * @param companyData         company repository
     * @param orderData           order repository
     * @param itemReturnService   gives workers their delivered items back
     * @param notificationService tells owners their orders are open again
     * @param treasuryService     empties the treasury and pays it to the owner
     */
    public DisbandService(
            Plugin plugin,
            ACompanyData companyData,
            AOrderData orderData,
            ItemReturnService itemReturnService,
            NotificationService notificationService,
            TreasuryService treasuryService) {
        this.plugin = plugin;
        this.companyData = companyData;
        this.orderData = orderData;
        this.itemReturnService = itemReturnService;
        this.notificationService = notificationService;
        this.treasuryService = treasuryService;
    }

    /**
     * Dissolves a company on behalf of its owner. Blocks, so it must not run on the main thread.
     *
     * @param company company to dissolve
     * @return true if the company was deleted
     */
    public boolean disband(CompanyProfile company) {
        return disband(
                company,
                company.members(CompanyPermission.OWNER).stream()
                        .findFirst()
                        .map(CompanyMember::uuid)
                        .orElse(null));
    }

    /**
     * Dissolves the company of its owner, as {@code /company disband} does once confirmed: only the owner dissolves,
     * and only the company the owner was asked about. Blocks, so it must not run on the main thread.
     *
     * @param actor   the owner
     * @param company id of the company the owner confirmed
     * @return the dissolved company, or why it was not dissolved
     */
    public ActionResult<CompanyProfile> disbandOwned(UUID actor, int company) {
        var found = companyData
                .retrievePlayerCompanyProfile(plugin.getServer().getOfflinePlayer(actor))
                .asFuture()
                .join();
        if (found.isEmpty() || found.get().id() != company) return ActionResult.refusedActionBar("error.noMember");
        var profile = found.get();
        if (!profile.member(actor).map(CompanyMember::isOwner).orElse(false)) {
            return ActionResult.refusedActionBar("error.noOwner");
        }
        if (!disband(profile, actor)) return ActionResult.refusedChat("company.disband.failed");
        return ActionResult.done(profile);
    }

    /**
     * Releases the company's orders, empties its treasury and deletes the company. Blocks, so it must not run on the
     * main thread.
     *
     * @param company company to dissolve
     * @param actor   owner or admin dissolving it
     * @return true if the company was deleted
     */
    public boolean disband(CompanyProfile company, @Nullable UUID actor) {
        var released = orderData.submitCompanyOrdersPurge(company).join();
        if (released != null) {
            for (var release : released.releases()) {
                itemReturnService.returned(
                        release.order(), release.returned(), ItemsReturnedEvent.Reason.COMPANY_DISBANDED);
                notificationService.notifyOrderOpen(release.order());
            }
        }
        var drained = treasuryService.drain(company, actor).join();
        if (!Boolean.TRUE.equals(companyData.submitCompanyPurge(company).join())) {
            treasuryService.undoDrain(company, drained, actor).join();
            return false;
        }
        treasuryService.payOwner(company, drained);
        AsyncEvents.fire(plugin, new CompanyDisbandEvent(company));
        return true;
    }
}
