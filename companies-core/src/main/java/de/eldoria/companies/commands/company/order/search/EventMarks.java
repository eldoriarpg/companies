/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.services.events.EventPreviews.EventMark;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Finds what the server events running now do to found orders, off the main thread.
 */
@FunctionalInterface
public interface EventMarks {
    /**
     * No server events.
     */
    EventMarks NONE = (orders, level) -> Map.of();

    /**
     * @param orders the found orders
     * @param level  the level of the searching company, empty for a player working alone
     * @return the mark of each order an event boosts, by order id
     */
    Map<Integer, EventMark> marks(List<FullOrder> orders, OptionalInt level);
}
