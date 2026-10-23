/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.CompanyProvider;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A company accepted an order and claimed it.
 * <p>
 * Fired after the order was claimed, off the main thread. It fires only on the server where the order was accepted.
 * {@link PreOrderAcceptEvent} is fired before and can prevent it. A player claiming an order alone fires
 * {@link SoloOrderAcceptEvent} instead.
 * <p>
 * Orders are anonymous: never show the owner to the company's members or the company to the owner.
 */
public class OrderAcceptEvent extends OrderEvent<ISimpleOrder> implements CompanyProvider<ICompanyProfile> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final ICompanyProfile company;

    /**
     * Creates a new event.
     *
     * @param order   the claimed order
     * @param company company that claimed the order
     */
    public OrderAcceptEvent(ISimpleOrder order, ICompanyProfile company) {
        super(order, true);
        this.company = company;
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
     * The company that claimed the order.
     *
     * @return company
     */
    @Override
    public ICompanyProfile company() {
        return company;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
