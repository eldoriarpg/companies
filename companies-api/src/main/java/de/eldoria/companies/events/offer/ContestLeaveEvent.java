/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.offer;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.offer.IEventOffer;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A company is about to leave a contest, for one of its members with the {@code manage_orders} permission. It keeps
 * its place and what it delivered.
 * <p>
 * Fired on the server where the member leaves, after the checks passed. Cancelling it keeps the company in the
 * contest.
 */
public class ContestLeaveEvent extends ContestMembershipEvent {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    /**
     * Creates a new event, asynchronous when created off the main thread.
     *
     * @param offer   the contest
     * @param company the company
     * @param member  the member acting for it
     */
    public ContestLeaveEvent(IEventOffer offer, ICompanyProfile company, OfflinePlayer member) {
        super(offer, company, member);
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
