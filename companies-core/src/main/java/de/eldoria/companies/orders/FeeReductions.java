/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.services.events.EventEffects;
import de.eldoria.companies.util.Money;

import java.util.Collection;
import java.util.List;

/**
 * The fee reductions of the server events running now. A fee reduction has no level scope: it applies to every order
 * with a line accepting one of its materials, or to every order when it has no material scope; a criteria line accepts
 * every material of its criteria, see {@link OrderMaterials#accepted}. The reductions of several
 * events add up, and a fee never drops below 0; the order stores the reduced fee, so what is given back later is what
 * was paid.
 */
@FunctionalInterface
public interface FeeReductions {
    /**
     * No reductions.
     */
    FeeReductions NONE = materials -> List.of();

    /**
     * The events running now that apply to an order. May read the database; call it off the main thread.
     *
     * @param materials every material the order's lines accept
     * @return the events, of any kind
     */
    List<ServerEvent> active(Collection<String> materials);

    /**
     * The fee after the reductions of the events running now, rounded down to the cent.
     *
     * @param fee       the fee without events
     * @param materials the materials of the order's lines
     * @return the reduced fee
     */
    default double fee(double fee, Collection<String> materials) {
        if (!(fee > 0)) return fee;
        var reductions = active(materials).stream()
                .filter(event -> event.kind() == EventKind.FEE_REDUCTION)
                .toList();
        if (reductions.isEmpty()) return fee;
        return Money.floor(EventEffects.fee(fee, reductions));
    }
}
