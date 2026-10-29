/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

import de.eldoria.companies.components.order.OrderState;
import org.jetbrains.annotations.Nullable;

/**
 * A chunk of a goal that is still open, with the state of its order.
 *
 * @param order   id of the order
 * @param amount  items it asks for
 * @param paid    what was put into it
 * @param account economy account that paid it, null for minted money
 * @param state   the state of its order, null when the order no longer exists
 */
public record OpenChunk(
        int order,
        int amount,
        double paid,
        @Nullable String account,
        @Nullable OrderState state) {
    /**
     * @return whether its order waits to be taken
     */
    public boolean unclaimed() {
        return state == OrderState.UNCLAIMED;
    }
}
