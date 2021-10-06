package de.eldoria.companies.events.order;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.CompanyProvider;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * All items for a order are delivered and the order is ready for retrieval.
 */
public class OrderDoneEvent extends OrderEvent<ISimpleOrder> implements CompanyProvider<ICompanyProfile> {
    public static HandlerList HANDLERS = new HandlerList();

    private final ICompanyProfile company;

    public OrderDoneEvent(ISimpleOrder order, ICompanyProfile company) {
        super(order, true);
        this.company = company;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
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
}
