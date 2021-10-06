package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.ISimpleOrder;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A active and claimed order was canceled.
 * <p>
 * This event will not be fired when a order is aborted by the order owner.
 * <p>
 * This event will not be called when an order expires. See {@link OrderExpiredEvent}.
 */
public class OrderRemovedEvent extends OrderEvent<ISimpleOrder> {
    public static HandlerList HANDLERS = new HandlerList();

    public OrderRemovedEvent(ISimpleOrder order) {
        super(order, true);
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
