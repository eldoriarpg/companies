/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.job;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.job.IJob;
import de.eldoria.companies.events.components.CompanyProvider;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A company is about to take an open job, for one of its members with the {@code manage_orders} permission.
 * <p>
 * Fired on the server where the member takes it, after every check passed: the job is open, not the member's company's
 * own, and the company has a work slot free. Cancelling it leaves the job open. It may still not happen when another
 * company was faster.
 */
public class JobTakeEvent extends CancellableJobEvent
        implements CompanyProvider<ICompanyProfile>, PlayerProvider<OfflinePlayer> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final ICompanyProfile company;
    private final OfflinePlayer member;

    /**
     * Creates a new event, asynchronous when created off the main thread.
     *
     * @param job     the job, still open
     * @param company the company
     * @param member  the member taking it
     */
    public JobTakeEvent(IJob job, ICompanyProfile company, OfflinePlayer member) {
        super(job);
        this.company = Objects.requireNonNull(company, "company");
        this.member = Objects.requireNonNull(member, "member");
    }

    /**
     * The company taking the job.
     *
     * @return company
     */
    @Override
    public ICompanyProfile company() {
        return company;
    }

    /**
     * The member taking the job for the company.
     *
     * @return member
     */
    @Override
    public OfflinePlayer player() {
        return member;
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
