/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import java.util.List;

/**
 * An order of the player as its id is suggested: the id, its state, and a tooltip with the name and the contents.
 * Nothing about the company working on it is kept, since orders are anonymous.
 *
 * @param id      id of the order
 * @param state   state of the order
 * @param tooltip name and contents of the order
 */
public record OwnOrder(int id, OrderState state, Component tooltip) {
    /**
     * @param orders orders of the player
     * @return the orders as they are suggested, in their order
     */
    public static List<OwnOrder> of(List<FullOrder> orders) {
        return orders.stream().map(OwnOrder::of).toList();
    }

    /**
     * @param order order of the player
     * @return the order as it is suggested
     */
    public static OwnOrder of(FullOrder order) {
        var contents = order.contents().stream()
                .map(content -> Component.text(content.amount() + "× ").append(content.itemName()))
                .toList();
        var tooltip =
                Component.text(order.name() + ": ").append(Component.join(JoinConfiguration.commas(true), contents));
        return new OwnOrder(order.id(), order.state(), tooltip);
    }
}
