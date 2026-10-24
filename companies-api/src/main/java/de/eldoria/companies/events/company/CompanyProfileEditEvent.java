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
 * A member changed the description, tag, colour or join mode of a company.
 * <p>
 * Fired after the change was saved, only on the server where the change was made. The event may be asynchronous.
 */
public class CompanyProfileEditEvent extends CompanyEvent<ICompanyProfile> implements PlayerProvider<OfflinePlayer> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final ICompanyProfile previous;
    private final OfflinePlayer player;

    /**
     * Creates a new event.
     *
     * @param previous profile before the change
     * @param company  profile after the change
     * @param player   member who made the change
     */
    public CompanyProfileEditEvent(ICompanyProfile previous, ICompanyProfile company, OfflinePlayer player) {
        super(company, !Bukkit.isPrimaryThread());
        this.previous = previous;
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
     * The profile as it was before the change.
     *
     * @return previous profile
     */
    public ICompanyProfile previous() {
        return previous;
    }

    /**
     * The member who made the change.
     *
     * @return player
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
