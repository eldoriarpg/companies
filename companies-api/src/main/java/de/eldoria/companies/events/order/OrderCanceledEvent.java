package de.eldoria.companies.events.order;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.CompanyProvider;

/**
 * A active and claimed order was canceled.
 * <p>
 * This event will not be fired when a order is aborted by the order owner.
 * <p>
 * This event will not be called when an order expires. See {@link OrderExpiredEvent}.
 */
public class OrderCanceledEvent extends OrderEvent<ISimpleOrder> implements CompanyProvider<ICompanyProfile> {
    private final ICompanyProfile company;

    public OrderCanceledEvent(ISimpleOrder order, ICompanyProfile company) {
        super(order);
        this.company = company;
    }

    @Override
    public ICompanyProfile company() {
        return company;
    }
}
