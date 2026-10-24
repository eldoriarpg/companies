/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * A member took back an invite of their company.
 * <p>
 * Fired after the invite was removed, off the main thread, only on the server where it was taken back.
 */
public class CompanyInviteRevokeEvent extends CompanyEvent<ICompanyProfile> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID target;
    private final UUID actor;

    /**
     * Creates a new event.
     *
     * @param company the inviting company
     * @param target  id of the player who was invited
     * @param actor   id of the member who took the invite back
     */
    public CompanyInviteRevokeEvent(ICompanyProfile company, UUID target, UUID actor) {
        super(company, true);
        this.target = Objects.requireNonNull(target, "target");
        this.actor = Objects.requireNonNull(actor, "actor");
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
     * The player who was invited.
     *
     * @return id of the player
     */
    public UUID target() {
        return target;
    }

    /**
     * The member who took the invite back.
     *
     * @return id of the member
     */
    public UUID actor() {
        return actor;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
