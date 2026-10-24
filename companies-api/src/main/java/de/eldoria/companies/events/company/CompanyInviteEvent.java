/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A member invited a player into a company. A renewed invite to the same player fires the event again.
 * <p>
 * Fired after the invite was saved, off the main thread, only on the server where the invite was sent.
 */
public class CompanyInviteEvent extends CompanyEvent<ICompanyProfile> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID target;
    private final UUID inviter;
    private final LocalDateTime expires;

    /**
     * Creates a new event.
     *
     * @param company the inviting company
     * @param target  id of the invited player
     * @param inviter id of the member who sent the invite
     * @param expires when the invite runs out
     */
    public CompanyInviteEvent(ICompanyProfile company, UUID target, UUID inviter, LocalDateTime expires) {
        super(company, true);
        this.target = Objects.requireNonNull(target, "target");
        this.inviter = Objects.requireNonNull(inviter, "inviter");
        this.expires = Objects.requireNonNull(expires, "expires");
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
     * The invited player.
     *
     * @return id of the player
     */
    public UUID target() {
        return target;
    }

    /**
     * The member who sent the invite.
     *
     * @return id of the member
     */
    public UUID inviter() {
        return inviter;
    }

    /**
     * When the invite runs out, in the server's local time.
     *
     * @return expiry time
     */
    public LocalDateTime expires() {
        return expires;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
