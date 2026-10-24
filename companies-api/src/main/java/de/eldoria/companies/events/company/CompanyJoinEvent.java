/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A player joined a company: by an open join, an accepted invite or application, or through the API.
 * <p>
 * Fired after the membership was saved. The event may be asynchronous.
 */
public class CompanyJoinEvent extends CompanyEvent<ICompanyProfile> implements PlayerProvider<OfflinePlayer> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer player;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param company the company with its new member
     * @param player  player who joined
     */
    public CompanyJoinEvent(ICompanyProfile company, OfflinePlayer player) {
        super(company, !Bukkit.isPrimaryThread());
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
     * The player who joined.
     *
     * @return new member
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
