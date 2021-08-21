package de.eldoria.companies.events.order;

import de.eldoria.companies.data.wrapper.order.SimpleOrder;

public interface OrderProvider<Order extends SimpleOrder> {
    Order order();
}
