/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.company.CompanyAreasChangeEvent;
import de.eldoria.companies.events.company.CompanyCreateEvent;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.events.company.CompanyJoinEvent;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.companies.events.company.CompanyLevelDownEvent;
import de.eldoria.companies.events.company.CompanyLevelUpEvent;
import de.eldoria.companies.events.company.CompanyMemberPermissionEvent;
import de.eldoria.companies.events.company.CompanyOwnerTransferEvent;
import de.eldoria.companies.events.company.CompanyProfileEditEvent;
import de.eldoria.companies.events.company.CompanyRenameEvent;
import de.eldoria.companies.events.company.TreasuryTransactionEvent;
import de.eldoria.companies.events.offer.ContestDeliveryEvent;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.OrderExpiredEvent;
import de.eldoria.companies.events.order.OrderReceivedEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.events.order.SoloOrderAbortEvent;
import de.eldoria.companies.events.order.SoloOrderAcceptEvent;
import de.eldoria.companies.events.order.SoloOrderDeliveryEvent;
import de.eldoria.companies.events.order.SoloOrderDoneEvent;
import de.eldoria.companies.events.order.SoloOrderExpiredEvent;
import de.eldoria.companies.events.order.SoloOrderReceivedEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps the placeholder caches current on this node: players are kept while online, and companies, players and the
 * rankings are reloaded after the local events that change them; the rankings and the event offers reload soon, once
 * for the changes of a moment. Changes on other nodes arrive with the network's cache invalidations and offer
 * announcements, or with the periodic refresh.
 */
public final class PlaceholderEvents implements Listener {
    private final PlayerPlaceholderCache players;
    private final CompanyPlaceholderCache companies;
    private final OfferPlaceholderCache offers;
    private final LeaderboardCache leaderboards;

    /**
     * @param players      values of the players
     * @param companies    values of the companies
     * @param offers       the event offers that run
     * @param leaderboards the rankings
     */
    public PlaceholderEvents(
            PlayerPlaceholderCache players,
            CompanyPlaceholderCache companies,
            OfferPlaceholderCache offers,
            LeaderboardCache leaderboards) {
        this.players = players;
        this.companies = companies;
        this.offers = offers;
        this.leaderboards = leaderboards;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onContestDelivery(ContestDeliveryEvent event) {
        offers.refreshSoon();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        players.join(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        players.quit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyCreate(CompanyCreateEvent event) {
        players.invalidate(event.player().getUniqueId());
        leaderboards.refreshSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyJoin(CompanyJoinEvent event) {
        players.invalidate(event.player().getUniqueId());
        companies.invalidate(event.company().id());
        leaderboards.refreshSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyLeave(CompanyLeaveEvent event) {
        players.invalidate(event.player().getUniqueId());
        companies.invalidate(event.company().id());
        leaderboards.refreshSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyDisband(CompanyDisbandEvent event) {
        companies.remove(event.company().id());
        players.invalidateCompany(event.company().id());
        leaderboards.refreshSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyRename(CompanyRenameEvent event) {
        companies.invalidate(event.company().id());
        leaderboards.refreshSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberPermission(CompanyMemberPermissionEvent event) {
        players.invalidate(event.member().uuid());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOwnerTransfer(CompanyOwnerTransferEvent event) {
        companies.invalidate(event.company().id());
        players.invalidate(event.previousOwner().getUniqueId());
        players.invalidate(event.newOwner().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTreasuryTransaction(TreasuryTransactionEvent event) {
        companies.invalidate(event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAreas(CompanyAreasChangeEvent event) {
        companies.invalidate(event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileEdit(CompanyProfileEditEvent event) {
        companies.invalidate(event.company().id());
        leaderboards.refreshSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelUp(CompanyLevelUpEvent event) {
        companies.invalidate(event.company().id());
        leaderboards.refreshSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelDown(CompanyLevelDownEvent event) {
        companies.invalidate(event.company().id());
        leaderboards.refreshSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderAccept(OrderAcceptEvent event) {
        changed(event.order(), event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderDone(OrderDoneEvent event) {
        changed(event.order(), event.company().id());
        leaderboards.refreshSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderCanceled(OrderCanceledEvent event) {
        changed(event.order(), event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderExpired(OrderExpiredEvent event) {
        changed(event.order(), event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderReceived(OrderReceivedEvent event) {
        players.invalidate(event.order().owner());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemsReturned(ItemsReturnedEvent event) {
        players.invalidate(event.player().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderRemoved(OrderRemovedEvent event) {
        players.invalidate(event.order().owner());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderAccept(SoloOrderAcceptEvent event) {
        soloChanged(event.order(), event.player());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderDone(SoloOrderDoneEvent event) {
        soloChanged(event.order(), event.player());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderAbort(SoloOrderAbortEvent event) {
        soloChanged(event.order(), event.player());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderExpired(SoloOrderExpiredEvent event) {
        soloChanged(event.order(), event.player());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderDelivery(SoloOrderDeliveryEvent event) {
        soloChanged(event.order(), event.player());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderReceived(SoloOrderReceivedEvent event) {
        soloChanged(event.order(), event.player());
    }

    private void soloChanged(ISimpleOrder order, OfflinePlayer worker) {
        players.invalidate(order.owner());
        players.invalidate(worker.getUniqueId());
    }

    private void changed(ISimpleOrder order, int company) {
        players.invalidate(order.owner());
        companies.invalidate(company);
    }
}
