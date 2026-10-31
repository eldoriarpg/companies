/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.feed;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.company.CompanyAreasChangeEvent;
import de.eldoria.companies.events.company.CompanyCreateEvent;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.events.company.CompanyInviteAnswerEvent;
import de.eldoria.companies.events.company.CompanyInviteEvent;
import de.eldoria.companies.events.company.CompanyInviteRevokeEvent;
import de.eldoria.companies.events.company.CompanyJoinEvent;
import de.eldoria.companies.events.company.CompanyJoinModeChangeEvent;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.companies.events.company.CompanyLevelChangeEvent;
import de.eldoria.companies.events.company.CompanyMemberPermissionEvent;
import de.eldoria.companies.events.company.CompanyOwnerTransferEvent;
import de.eldoria.companies.events.company.CompanyProfileEditEvent;
import de.eldoria.companies.events.company.CompanyRenameEvent;
import de.eldoria.companies.events.company.TreasuryTransactionEvent;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.events.order.OrderCreatedEvent;
import de.eldoria.companies.events.order.OrderDeliveryEvent;
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
import de.eldoria.companies.services.notifications.InboxEntryCreatedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Turns the plugin's events into dashboard patches, see {@link PatchFeed}. Events are node-local; every node pushes
 * the patches of the events that happened on it.
 */
public final class FeedListener implements Listener {
    private final PatchFeed feed;

    /**
     * @param feed the feed the changes go to
     */
    public FeedListener(PatchFeed feed) {
        this.feed = feed;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderCreated(OrderCreatedEvent event) {
        order(event.order());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderAccept(OrderAcceptEvent event) {
        order(event.order());
    }

    /**
     * Updates the order for its owner and company, and the delivering member for the company, whose items grow with
     * the order's line.
     *
     * @param event the delivery
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderDelivery(OrderDeliveryEvent event) {
        order(event.order());
        feed.memberChanged(event.company().id(), event.player().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderDone(OrderDoneEvent event) {
        order(event.order());
        feed.rankingsChanged();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderCanceled(OrderCanceledEvent event) {
        feed.orderChanged(event.order(), event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderExpired(OrderExpiredEvent event) {
        feed.orderChanged(event.order(), event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderRemoved(OrderRemovedEvent event) {
        feed.orderChanged(event.order(), event.order().company());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderReceived(OrderReceivedEvent event) {
        feed.orderChanged(event.order(), event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloAccept(SoloOrderAcceptEvent event) {
        order(event.order());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloAbort(SoloOrderAbortEvent event) {
        order(event.order());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloExpired(SoloOrderExpiredEvent event) {
        order(event.order());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloDelivery(SoloOrderDeliveryEvent event) {
        order(event.order());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloDone(SoloOrderDoneEvent event) {
        order(event.order());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloReceived(SoloOrderReceivedEvent event) {
        order(event.order());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyCreate(CompanyCreateEvent event) {
        feed.playerChanged(event.player().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyJoin(CompanyJoinEvent event) {
        feed.memberJoined(event.company().id(), event.player().getUniqueId());
    }

    /**
     * Shows the player without the company and the company without the player, for kicks too.
     *
     * @param event the leave or kick
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyLeave(CompanyLeaveEvent event) {
        feed.memberLeft(event.company().id(), event.player().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyDisband(CompanyDisbandEvent event) {
        feed.companyChanged(event.company().id());
        feed.rankingsChanged();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyRename(CompanyRenameEvent event) {
        feed.companyChanged(event.company().id());
        feed.rankingsChanged();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileEdit(CompanyProfileEditEvent event) {
        feed.companyChanged(event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelChange(CompanyLevelChangeEvent event) {
        feed.companyChanged(event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOwnerTransfer(CompanyOwnerTransferEvent event) {
        feed.companyChanged(event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinModeChange(CompanyJoinModeChangeEvent event) {
        feed.companyChanged(event.company().id());
    }

    /**
     * Shows the company the member's new permissions, and the member what they allow now.
     *
     * @param event the permission change
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberPermission(CompanyMemberPermissionEvent event) {
        feed.memberChanged(event.company().id(), event.member().uuid());
        feed.playerChanged(event.member().uuid());
    }

    /**
     * Shows the company its areas and home after they changed.
     *
     * @param event the change
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAreas(CompanyAreasChangeEvent event) {
        feed.companyChanged(event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTreasury(TreasuryTransactionEvent event) {
        feed.treasuryChanged(event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInvite(CompanyInviteEvent event) {
        feed.invitesChanged(event.target());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInviteRevoke(CompanyInviteRevokeEvent event) {
        feed.invitesChanged(event.target());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInviteAnswer(CompanyInviteAnswerEvent event) {
        feed.invitesChanged(event.target());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInboxEntry(InboxEntryCreatedEvent event) {
        feed.inboxChanged(event.entry().recipient());
    }

    private void order(ISimpleOrder order) {
        feed.orderChanged(order, null);
    }
}
