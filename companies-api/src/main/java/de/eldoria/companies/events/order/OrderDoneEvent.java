/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.CompanyProvider;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * All items of an order are delivered and the owner can receive it.
 * <p>
 * Fired after the order changed to {@link de.eldoria.companies.components.order.OrderState#DELIVERED}, off the main
 * thread. It fires only on the server where the last delivery happened. The workers are paid afterwards, see
 * {@link OrderPaymentEvent}. An order a player delivered alone fires {@link SoloOrderDoneEvent} instead.
 * <p>
 * Orders are anonymous: never show the owner to the company's members or the company to the owner.
 */
public class OrderDoneEvent extends OrderEvent<ISimpleOrder> implements CompanyProvider<ICompanyProfile> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final ICompanyProfile company;

    /**
     * Creates a new event.
     *
     * @param order   the delivered order
     * @param company company that delivered the order
     */
    public OrderDoneEvent(ISimpleOrder order, ICompanyProfile company) {
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
     * The company that delivered the order.
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
