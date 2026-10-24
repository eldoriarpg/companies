/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.company.CompanyApplicationAnswerEvent;
import de.eldoria.companies.events.company.CompanyApplicationEvent;
import de.eldoria.companies.events.company.CompanyApplicationWithdrawEvent;
import de.eldoria.companies.events.company.CompanyCreateEvent;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.events.company.CompanyEvent;
import de.eldoria.companies.events.company.CompanyInviteAnswerEvent;
import de.eldoria.companies.events.company.CompanyInviteEvent;
import de.eldoria.companies.events.company.CompanyInviteRevokeEvent;
import de.eldoria.companies.events.company.CompanyJoinEvent;
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
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.OrderExpiredEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.events.order.SoloOrderAbortEvent;
import de.eldoria.companies.events.order.SoloOrderAcceptEvent;
import de.eldoria.companies.events.order.SoloOrderDeliveryEvent;
import de.eldoria.companies.events.order.SoloOrderDoneEvent;
import de.eldoria.companies.events.order.SoloOrderExpiredEvent;
import de.eldoria.companies.events.order.SoloOrderReceivedEvent;
import de.eldoria.companies.nodes.NodeMessage.BoardsChanged;
import de.eldoria.companies.nodes.NodeMessage.CacheInvalidate;
import de.eldoria.companies.nodes.NodeMessage.PlayerPresence;
import de.eldoria.companies.nodes.NodeMessage.RankingChanged;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Tells the other nodes about the changes made on this node, by turning the plugin's events into node messages.
 * <p>
 * Events stay node-local; only the signals cross: a changed company invalidates its cached values, order changes
 * outdate the board sign counts, completed orders and level changes outdate the rankings, orders worked on alone
 * outdate the values of their owner and worker, invites and applications outdate the values of the invited or applying
 * player, and joins and quits keep the presence of players current. A changed join mode is announced as a profile edit.
 * Changed single orders are announced by the order repository itself. On the {@link DatabaseBus} every message is
 * dropped.
 */
public final class NodeSignals implements Listener {
    private final NodeBus bus;

    /**
     * @param bus the bus to publish on
     */
    public NodeSignals(NodeBus bus) {
        this.bus = bus;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        bus.publish(new PlayerPresence(event.getPlayer().getUniqueId(), true));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        bus.publish(new PlayerPresence(event.getPlayer().getUniqueId(), false));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyCreate(CompanyCreateEvent event) {
        companyChanged(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyJoin(CompanyJoinEvent event) {
        companyChanged(event);
    }

    /**
     * Announces a member leaving, kicks included.
     *
     * @param event leave or kick event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyLeave(CompanyLeaveEvent event) {
        companyChanged(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyDisband(CompanyDisbandEvent event) {
        companyChanged(event);
        bus.publish(new RankingChanged());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyRename(CompanyRenameEvent event) {
        companyChanged(event);
        bus.publish(new RankingChanged());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileEdit(CompanyProfileEditEvent event) {
        companyChanged(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberPermission(CompanyMemberPermissionEvent event) {
        companyChanged(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInvite(CompanyInviteEvent event) {
        playerChanged(event.target());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInviteRevoke(CompanyInviteRevokeEvent event) {
        playerChanged(event.target());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInviteAnswer(CompanyInviteAnswerEvent event) {
        playerChanged(event.target());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onApplication(CompanyApplicationEvent event) {
        playerChanged(event.applicant());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onApplicationWithdraw(CompanyApplicationWithdrawEvent event) {
        playerChanged(event.applicant());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onApplicationAnswer(CompanyApplicationAnswerEvent event) {
        playerChanged(event.applicant());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOwnerTransfer(CompanyOwnerTransferEvent event) {
        companyChanged(event);
    }

    /**
     * Announces a level change, up or down.
     *
     * @param event level change event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelChange(CompanyLevelChangeEvent event) {
        companyChanged(event);
        bus.publish(new RankingChanged());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTreasuryTransaction(TreasuryTransactionEvent event) {
        companyChanged(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderCreated(OrderCreatedEvent event) {
        bus.publish(new BoardsChanged());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderAccept(OrderAcceptEvent event) {
        companyChanged(event.company().id());
        bus.publish(new BoardsChanged());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderCanceled(OrderCanceledEvent event) {
        companyChanged(event.company().id());
        bus.publish(new BoardsChanged());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderExpired(OrderExpiredEvent event) {
        companyChanged(event.company().id());
        bus.publish(new BoardsChanged());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderRemoved(OrderRemovedEvent event) {
        bus.publish(new BoardsChanged());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderDone(OrderDoneEvent event) {
        companyChanged(event.company().id());
        bus.publish(new RankingChanged());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderAccept(SoloOrderAcceptEvent event) {
        soloChanged(event.order(), event.player());
        bus.publish(new BoardsChanged());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderAbort(SoloOrderAbortEvent event) {
        soloChanged(event.order(), event.player());
        bus.publish(new BoardsChanged());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderExpired(SoloOrderExpiredEvent event) {
        soloChanged(event.order(), event.player());
        bus.publish(new BoardsChanged());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderDelivery(SoloOrderDeliveryEvent event) {
        soloChanged(event.order(), event.player());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderDone(SoloOrderDoneEvent event) {
        soloChanged(event.order(), event.player());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderReceived(SoloOrderReceivedEvent event) {
        soloChanged(event.order(), event.player());
    }

    private void soloChanged(ISimpleOrder order, OfflinePlayer worker) {
        playerChanged(order.owner());
        playerChanged(worker.getUniqueId());
    }

    private void playerChanged(UUID player) {
        bus.publish(new CacheInvalidate(NodeCaches.PLAYER, player.toString()));
    }

    private void companyChanged(CompanyEvent<?> event) {
        companyChanged(event.company().id());
    }

    private void companyChanged(int company) {
        bus.publish(new CacheInvalidate(NodeCaches.COMPANY, String.valueOf(company)));
    }
}
