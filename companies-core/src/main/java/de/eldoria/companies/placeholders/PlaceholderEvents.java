/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.events.company.CompanyJoinEvent;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.companies.events.company.CompanyLevelDownEvent;
import de.eldoria.companies.events.company.CompanyLevelUpEvent;
import de.eldoria.companies.events.company.CompanyProfileEditEvent;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.OrderExpiredEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps the placeholder caches current on this node: players are kept while online, and companies and players are
 * reloaded when a local event changes them. Changes on other nodes arrive with the periodic refresh.
 */
public final class PlaceholderEvents implements Listener {
    private final PlayerPlaceholderCache players;
    private final CompanyPlaceholderCache companies;

    /**
     * @param players   values of the players
     * @param companies values of the companies
     */
    public PlaceholderEvents(PlayerPlaceholderCache players, CompanyPlaceholderCache companies) {
        this.players = players;
        this.companies = companies;
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
    public void onCompanyJoin(CompanyJoinEvent event) {
        players.invalidate(event.player().getUniqueId());
        companies.invalidate(event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyLeave(CompanyLeaveEvent event) {
        players.invalidate(event.player().getUniqueId());
        companies.invalidate(event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCompanyDisband(CompanyDisbandEvent event) {
        companies.remove(event.company().id());
        players.invalidateCompany(event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileEdit(CompanyProfileEditEvent event) {
        companies.invalidate(event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelUp(CompanyLevelUpEvent event) {
        companies.invalidate(event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelDown(CompanyLevelDownEvent event) {
        companies.invalidate(event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderAccept(OrderAcceptEvent event) {
        changed(event.order(), event.company().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderDone(OrderDoneEvent event) {
        changed(event.order(), event.company().id());
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
    public void onOrderRemoved(OrderRemovedEvent event) {
        players.invalidate(event.order().owner());
    }

    private void changed(ISimpleOrder order, int company) {
        players.invalidate(order.owner());
        companies.invalidate(company);
    }
}
