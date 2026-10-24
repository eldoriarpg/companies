/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.events.player.ItemsReturnedEvent.ReturnedStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The stacks delivered to an order that were moved to the item returns of their workers, by worker in delivery order.
 *
 * @param byWorker returned stacks of every worker
 */
public record ReturnedItems(Map<UUID, List<ReturnedStack>> byWorker) {
    /**
     * Nothing was returned.
     */
    public static final ReturnedItems NONE = new ReturnedItems(Map.of());

    /**
     * Copies the stacks and keeps the order of the workers.
     */
    public ReturnedItems {
        Map<UUID, List<ReturnedStack>> copy = new LinkedHashMap<>();
        byWorker.forEach((worker, stacks) -> copy.put(worker, List.copyOf(stacks)));
        byWorker = Collections.unmodifiableMap(copy);
    }

    /**
     * Groups returned stacks by their worker.
     *
     * @param stacks returned stacks in delivery order
     * @return the stacks by worker
     */
    public static ReturnedItems of(List<Map.Entry<UUID, ReturnedStack>> stacks) {
        Map<UUID, List<ReturnedStack>> byWorker = new LinkedHashMap<>();
        for (var entry : stacks) {
            byWorker.computeIfAbsent(entry.getKey(), worker -> new ArrayList<>())
                    .add(entry.getValue());
        }
        return new ReturnedItems(byWorker);
    }

    /**
     * @return the workers who got items returned
     */
    public Set<UUID> workers() {
        return byWorker.keySet();
    }
}
