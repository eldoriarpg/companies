package de.eldoria.companies.events.order;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.CompanyProvider;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A active and claimed order was canceled.
 * <p>
 * This event will not be fired when a order is aborted by the order owner.
 * <p>
 * This event will not be called when an order expires. See {@link OrderExpiredEvent}.
 */
public class OrderCanceledEvent extends OrderEvent<ISimpleOrder> implements CompanyProvider<ICompanyProfile> {
    public static HandlerList HANDLERS = new HandlerList();

    private final ICompanyProfile company;

    public OrderCanceledEvent(ISimpleOrder order, ICompanyProfile company) {
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
