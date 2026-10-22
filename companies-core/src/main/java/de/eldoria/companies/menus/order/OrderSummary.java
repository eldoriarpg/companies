/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.FullOrder;

import java.util.List;

/**
 * An order as one entry of an order list. Like {@link OrderLineView} it holds neither owner nor company.
 *
 * @param id    id of the order
 * @param name  name of the order
 * @param state state of the order
 * @param price price of the order
 * @param lines lines of the order
 */
public record OrderSummary(int id, String name, OrderState state, double price, List<OrderLineView> lines) {
    /**
     * @param order order to list
     * @return the entry
     */
    public static OrderSummary of(FullOrder order) {
        return new OrderSummary(order.id(), order.name(), order.state(), order.price(), OrderLineView.of(order));
    }
}
