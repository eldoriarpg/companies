/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ISimpleOrder {
    void name(String name);

    int id();

    UUID owner();

    String name();

    String fullName();

    LocalDateTime created();

    int company();

    LocalDateTime claimed();

    OrderState state();

    /**
     * The fee the owner paid for placing the order, on top of its price.
     * <p>
     * It is kept when the owner cancels the order and refunded when the order is removed because nobody accepted it.
     *
     * @return fee of the order
     */
    double fee();
}
