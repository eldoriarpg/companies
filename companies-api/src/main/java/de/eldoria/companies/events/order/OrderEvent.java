package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.OrderProvider;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Represends a basic order event.
 * <p>
 * This event is for internal use and should not be subscribed.
 *
 * @param <Order> Type of order implementation
 */
public class OrderEvent<Order extends ISimpleOrder> extends Event implements OrderProvider<Order> {
    public static HandlerList HANDLERS = new HandlerList();

    private final Order order;

    public OrderEvent(Order order) {
        super(false);
        this.order = order;
    }

    public OrderEvent(Order order, boolean async) {
        super(async);
        this.order = order;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public Order order() {
        return order;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
