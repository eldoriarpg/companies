package de.eldoria.companies.events.order;

import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

public class OrderEvent<Order extends SimpleOrder> extends Event implements OrderProvider<Order> {
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

    @Override
    public Order order() {
        return order;
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
