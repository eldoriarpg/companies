/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A member left a company or was removed from it.
 * <p>
 * Fired after the membership was deleted, off the main thread.
 */
public class CompanyLeaveEvent extends CompanyEvent<ICompanyProfile> implements PlayerProvider<OfflinePlayer> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer player;

    /**
     * Creates a new event.
     *
     * @param company the company without the member
     * @param player  player who left
     */
    public CompanyLeaveEvent(ICompanyProfile company, OfflinePlayer player) {
        super(company, true);
        this.player = player;
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    @SuppressWarnings("SameReturnValue")
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * The player who left.
     *
     * @return former member
     */
    @Override
    public OfflinePlayer player() {
        return player;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
