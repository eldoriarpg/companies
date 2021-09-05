package de.eldoria.companies.events.order;

import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.company.CompanyProvider;

/**
 * Indicates that a company has accepted a order.
 */
public class OrderAcceptEvent extends OrderEvent<SimpleOrder> implements CompanyProvider<CompanyProfile> {

    private final CompanyProfile company;

    public OrderAcceptEvent(SimpleOrder order, CompanyProfile company) {
        super(order);
        this.company = company;
    }

    @Override
    public CompanyProfile company() {
        return company;
    }
}
