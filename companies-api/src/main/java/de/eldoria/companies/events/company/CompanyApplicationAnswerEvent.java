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
 * A member accepted or denied an application to their company.
 * <p>
 * An accepted application fires this event after the applicant joined, so after {@link CompanyJoinEvent}. A denied
 * application fires it after the application was removed. Fired off the main thread, only on the server where the
 * member answered.
 */
public class CompanyApplicationAnswerEvent extends CompanyEvent<ICompanyProfile> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID applicant;
    private final UUID actor;
    private final boolean accepted;

    /**
     * Creates a new event.
     *
     * @param company   the company, with the new member when the application was accepted
     * @param applicant id of the applying player
     * @param actor     id of the member who answered
     * @param accepted  whether the application was accepted
     */
    public CompanyApplicationAnswerEvent(ICompanyProfile company, UUID applicant, UUID actor, boolean accepted) {
        super(company, true);
        this.applicant = Objects.requireNonNull(applicant, "applicant");
        this.actor = Objects.requireNonNull(actor, "actor");
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
     * The applying player.
     *
     * @return id of the player
     */
    public UUID applicant() {
        return applicant;
    }

    /**
     * The member who answered the application.
     *
     * @return id of the member
     */
    public UUID actor() {
        return actor;
    }

    /**
     * Whether the application was accepted and the applicant joined the company.
     *
     * @return true when accepted, false when denied
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
