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
 * A company did not deliver a claimed order in time; the order is open again for other companies.
 * <p>
 * Fired after the order was released, off the main thread, on the primary server of a network. A player running
 * out of time on an order they work on alone fires {@link SoloOrderExpiredEvent} instead.
 * <p>
 * Orders are anonymous: never show the owner to the company's members or the company to the owner.
 */
public class OrderExpiredEvent extends OrderEvent<ISimpleOrder> implements CompanyProvider<ICompanyProfile> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final ICompanyProfile company;

    /**
     * Creates a new event.
     *
     * @param order   the released order
     * @param company company that failed to deliver the order
     */
    public OrderExpiredEvent(ISimpleOrder order, ICompanyProfile company) {
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
     * The company that failed to deliver the order.
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
