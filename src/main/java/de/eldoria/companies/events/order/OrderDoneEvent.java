package de.eldoria.companies.events.order;

import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.company.CompanyProvider;

/**
 * Indicates that all items for a order are delivered and the order is ready for retrieval.
 */
public class OrderDoneEvent extends OrderEvent<SimpleOrder> implements CompanyProvider<CompanyProfile> {
    private final CompanyProfile company;

    public OrderDoneEvent(SimpleOrder order, CompanyProfile company) {
        super(order);
        this.company = company;
    }

    @Override
    public CompanyProfile company() {
        return company;
    }
}
