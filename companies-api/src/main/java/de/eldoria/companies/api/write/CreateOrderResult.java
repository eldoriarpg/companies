/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import de.eldoria.companies.components.order.IFullOrder;
import net.kyori.adventure.text.Component;

import java.util.Objects;

/**
 * The outcome of {@link de.eldoria.companies.api.OrderApi#create(OrderRequest)}: the created order or why it was not
 * created.
 */
public sealed interface CreateOrderResult {
    /**
     * Whether the order was created.
     *
     * @return true for {@link Created}
     */
    boolean created();

    /**
     * The order was created.
     *
     * @param order the saved order with its lines
     */
    record Created(IFullOrder order) implements CreateOrderResult {
        /**
         * Creates the result.
         */
        public Created {
            Objects.requireNonNull(order, "order");
        }

        @Override
        public boolean created() {
            return true;
        }
    }

    /**
     * The order was not created and nothing was charged.
     *
     * @param reason  why it was not created
     * @param message the message a player would have been shown, translatable or as given by the cancelling listener
     */
    record Rejected(RejectReason reason, Component message) implements CreateOrderResult {
        /**
         * Creates the result.
         */
        public Rejected {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(message, "message");
        }

        @Override
        public boolean created() {
            return false;
        }
    }
}
