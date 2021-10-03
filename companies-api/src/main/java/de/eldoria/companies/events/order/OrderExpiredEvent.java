package de.eldoria.companies.events.order;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.CompanyProvider;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A order expired.
 */
public class OrderExpiredEvent extends OrderEvent<ISimpleOrder> implements CompanyProvider<ICompanyProfile> {
    public static HandlerList HANDLERS = new HandlerList();

    private final ICompanyProfile company;

    public OrderExpiredEvent(ISimpleOrder order, ICompanyProfile company) {
        super(order, true);
        this.company = company;
    }

    @Override
    public ICompanyProfile company() {
        return company;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
