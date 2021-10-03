package de.eldoria.companies.events.order;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.CompanyProvider;

/**
 * All items for a order are delivered and the order is ready for retrieval.
 */
public class OrderDoneEvent extends OrderEvent<ISimpleOrder> implements CompanyProvider<ICompanyProfile> {
    private final ICompanyProfile company;

    public OrderDoneEvent(ISimpleOrder order, ICompanyProfile company) {
        super(order);
        this.company = company;
    }

    @Override
    public ICompanyProfile company() {
        return company;
    }
}
