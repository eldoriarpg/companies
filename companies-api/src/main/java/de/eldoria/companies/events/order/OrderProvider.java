package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.ISimpleOrder;

public interface OrderProvider<Order extends ISimpleOrder> {
    Order order();
}
