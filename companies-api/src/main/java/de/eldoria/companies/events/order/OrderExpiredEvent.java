package de.eldoria.companies.events.order;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.company.CompanyProvider;

public class OrderExpiredEvent extends OrderEvent<ISimpleOrder> implements CompanyProvider<ICompanyProfile> {
    private final ICompanyProfile company;

    public OrderExpiredEvent(ISimpleOrder order, ICompanyProfile company) {
        super(order);
        this.company = company;
    }

    @Override
    public ICompanyProfile company() {
        return company;
    }
}
