/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A company was founded.
 * <p>
 * Fired after the company was saved, usually off the main thread; the event may be asynchronous. It fires only on the
 * server where the company was founded.
 */
public class CompanyCreateEvent extends CompanyEvent<ICompanyProfile> implements PlayerProvider<OfflinePlayer> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer player;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param company the new company
     * @param player  player who founded the company
     */
    public CompanyCreateEvent(ICompanyProfile company, OfflinePlayer player) {
        super(company, !Bukkit.isPrimaryThread());
        this.player = Objects.requireNonNull(player, "player");
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * The player who founded the company and owns it.
     *
     * @return founder
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
