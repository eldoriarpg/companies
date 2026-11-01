/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.offer;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.offer.IEventOffer;
import de.eldoria.companies.events.components.CancellableWithReason;
import de.eldoria.companies.events.components.CompanyProvider;
import de.eldoria.companies.events.components.PlayerProvider;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * A company is about to join or leave a contest, for one of its members. Fired after every check passed and before
 * anything is stored; cancelling it stops the change and the member sees the {@link #cancelReason() reason}. It may
 * still not happen when another member of the company was faster.
 */
public abstract class ContestMembershipEvent extends OfferEvent
        implements CompanyProvider<ICompanyProfile>, PlayerProvider<OfflinePlayer>, CancellableWithReason {
    private final ICompanyProfile company;
    private final OfflinePlayer member;
    private boolean cancelled;

    @Nullable
    private Component reason;

    /**
     * Creates a new event, asynchronous when created off the main thread.
     *
     * @param offer   the contest
     * @param company the company
     * @param member  the member acting for it
     */
    protected ContestMembershipEvent(IEventOffer offer, ICompanyProfile company, OfflinePlayer member) {
        super(offer);
        this.company = Objects.requireNonNull(company, "company");
        this.member = Objects.requireNonNull(member, "member");
    }

    /**
     * The company joining or leaving.
     *
     * @return company
     */
    @Override
    public ICompanyProfile company() {
        return company;
    }

    /**
     * The member acting for the company.
     *
     * @return member
     */
    @Override
    public OfflinePlayer player() {
        return member;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
        if (!cancel) reason = null;
    }

    @Override
    public void cancel(Component reason) {
        this.reason = Objects.requireNonNull(reason, "reason");
        cancelled = true;
    }

    @Override
    public Optional<Component> cancelReason() {
        return cancelled ? Optional.ofNullable(reason) : Optional.empty();
    }
}
