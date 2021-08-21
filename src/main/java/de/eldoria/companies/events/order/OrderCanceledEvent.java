package de.eldoria.companies.events.order;

import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.company.CompanyProvider;

/**
 * Represent the cancellation of an active and claimed order.
 * <p>
 * This event will not be fired when a order is aborted by the order owner.
 * <p>
 * This event will not be called when an order expires. See {@link OrderExpiredEvent}.
 */
public class OrderCanceledEvent extends OrderEvent<SimpleOrder> implements CompanyProvider<CompanyProfile> {
    private CompanyProfile company;

    public OrderCanceledEvent(SimpleOrder order, CompanyProfile company) {
        super(order);
        this.company = company;
    }

    @Override
    public CompanyProfile company() {
        return company;
    }
}
