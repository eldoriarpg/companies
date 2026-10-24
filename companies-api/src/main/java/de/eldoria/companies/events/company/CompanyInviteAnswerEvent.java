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
 * An invited player accepted or declined an invite.
 * <p>
 * An accepted invite fires this event after the player joined, so after {@link CompanyJoinEvent}. A declined invite
 * fires it after the invite was removed. Fired off the main thread, only on the server where the player answered.
 */
public class CompanyInviteAnswerEvent extends CompanyEvent<ICompanyProfile> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID target;
    private final UUID inviter;
    private final boolean accepted;

    /**
     * Creates a new event.
     *
     * @param company  the inviting company, with the new member when the invite was accepted
     * @param target   id of the invited player
     * @param inviter  id of the member who sent the invite
     * @param accepted whether the player accepted the invite
     */
    public CompanyInviteAnswerEvent(ICompanyProfile company, UUID target, UUID inviter, boolean accepted) {
        super(company, true);
        this.target = Objects.requireNonNull(target, "target");
        this.inviter = Objects.requireNonNull(inviter, "inviter");
        this.accepted = accepted;
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
     * The invited player, who answered.
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
     * Whether the player accepted the invite and joined the company.
     *
     * @return true when accepted, false when declined
     */
    public boolean accepted() {
        return accepted;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
