/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ICompanyMember;
import de.eldoria.companies.components.company.ICompanyProfile;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A member granted or revoked permissions of another member. An edit that changes nothing does not fire the event.
 * <p>
 * Fired after the permissions were saved, off the main thread, only on the server where they were changed.
 */
public class CompanyMemberPermissionEvent extends CompanyEvent<ICompanyProfile> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ICompanyMember member;
    private final UUID actor;
    private final Set<CompanyPermission> previous;
    private final Set<CompanyPermission> current;

    /**
     * Creates a new event.
     *
     * @param company  the company after the change
     * @param member   the member whose permissions changed, with the new permissions
     * @param actor    id of the member who changed them
     * @param previous permissions of the member before the change
     */
    public CompanyMemberPermissionEvent(
            ICompanyProfile company, ICompanyMember member, UUID actor, Set<CompanyPermission> previous) {
        super(company, true);
        this.member = Objects.requireNonNull(member, "member");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.previous = Set.copyOf(previous);
        this.current = Set.copyOf(member.permissions());
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
     * The member whose permissions changed.
     *
     * @return member with the new permissions
     */
    public ICompanyMember member() {
        return member;
    }

    /**
     * The member who changed the permissions.
     *
     * @return id of the member
     */
    public UUID actor() {
        return actor;
    }

    /**
     * The permissions of the member before the change.
     *
     * @return permissions before
     */
    public Set<CompanyPermission> previous() {
        return previous;
    }

    /**
     * The permissions of the member after the change.
     *
     * @return permissions after
     */
    public Set<CompanyPermission> current() {
        return current;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
