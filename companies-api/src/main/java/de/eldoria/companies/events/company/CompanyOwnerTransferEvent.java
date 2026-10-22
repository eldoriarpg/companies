/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The ownership of a company passed to another member, by the owner or by an admin.
 * <p>
 * Fired after the change was saved, usually off the main thread; the event may be asynchronous. It fires only on the
 * server where the ownership was transferred.
 */
public class CompanyOwnerTransferEvent extends CompanyEvent<ICompanyProfile> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer previousOwner;
    private final OfflinePlayer newOwner;
    private final boolean byAdmin;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param company       the company after the change
     * @param previousOwner owner before the change
     * @param newOwner      owner after the change
     * @param byAdmin       whether the ownership was transferred with an admin command
     */
    public CompanyOwnerTransferEvent(
            ICompanyProfile company, OfflinePlayer previousOwner, OfflinePlayer newOwner, boolean byAdmin) {
        super(company, !Bukkit.isPrimaryThread());
        this.previousOwner = Objects.requireNonNull(previousOwner, "previousOwner");
        this.newOwner = Objects.requireNonNull(newOwner, "newOwner");
        this.byAdmin = byAdmin;
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
     * The owner before the change. They stay a member of the company.
     *
     * @return previous owner
     */
    public OfflinePlayer previousOwner() {
        return previousOwner;
    }

    /**
     * The owner after the change.
     *
     * @return new owner
     */
    public OfflinePlayer newOwner() {
        return newOwner;
    }

    /**
     * Whether the ownership was transferred with an admin command rather than by the owner.
     *
     * @return true for an admin transfer
     */
    public boolean byAdmin() {
        return byAdmin;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
