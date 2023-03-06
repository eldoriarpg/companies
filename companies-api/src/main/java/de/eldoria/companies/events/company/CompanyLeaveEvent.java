/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A user left a company.
 */
public class CompanyLeaveEvent extends CompanyEvent<ICompanyProfile> implements PlayerProvider<OfflinePlayer> {
    public static HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer player;

    public CompanyLeaveEvent(ICompanyProfile company, OfflinePlayer player) {
        super(company, true);
        this.player = player;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

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
