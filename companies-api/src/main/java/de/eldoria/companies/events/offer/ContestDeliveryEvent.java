/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.offer;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.offer.IEventOffer;
import de.eldoria.companies.events.components.CompanyProvider;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A worker delivered items into a contest for their company.
 * <p>
 * Fired after the delivery was stored and the worker paid, on the server where it was made; a payment that failed is
 * logged and the delivery stays. The offer shows the contest after
 * the delivery; one that filled the contest or spent its money is closed already and fires an {@link OfferEndEvent}
 * after this one.
 */
public class ContestDeliveryEvent extends OfferEvent
        implements CompanyProvider<ICompanyProfile>, PlayerProvider<OfflinePlayer> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final ICompanyProfile company;
    private final OfflinePlayer worker;
    private final int items;
    private final double paid;
    private final int place;
    private final int companyDelivered;

    /**
     * Creates a new event.
     *
     * @param offer            the contest after the delivery
     * @param company          the worker's company
     * @param worker           the worker
     * @param items            items taken
     * @param paid             what the items were paid before tax and treasury share
     * @param place            the company's place after the delivery, from 1
     * @param companyDelivered items the company delivered into the contest in all
     */
    public ContestDeliveryEvent(
            IEventOffer offer,
            ICompanyProfile company,
            OfflinePlayer worker,
            int items,
            double paid,
            int place,
            int companyDelivered) {
        super(offer);
        this.company = Objects.requireNonNull(company, "company");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.items = items;
        this.paid = paid;
        this.place = place;
        this.companyDelivered = companyDelivered;
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
     * The worker's company.
     *
     * @return company
     */
    @Override
    public ICompanyProfile company() {
        return company;
    }

    /**
     * The worker who delivered.
     *
     * @return worker
     */
    @Override
    public OfflinePlayer player() {
        return worker;
    }

    /**
     * The items the contest took.
     *
     * @return items
     */
    public int items() {
        return items;
    }

    /**
     * What the items were paid before the tax and the treasury share were taken.
     *
     * @return gross payment
     */
    public double paid() {
        return paid;
    }

    /**
     * The company's place in the contest after the delivery.
     *
     * @return place, from 1
     */
    public int place() {
        return place;
    }

    /**
     * The items the company delivered into the contest in all, this delivery included.
     *
     * @return delivered items
     */
    public int companyDelivered() {
        return companyDelivered;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
