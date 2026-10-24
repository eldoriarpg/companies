/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orders a company had to give up, each with the items its workers got returned.
 *
 * @param releases the released orders
 */
public record ReleasedOrders(List<Release> releases) {
    public ReleasedOrders {
        releases = List.copyOf(releases);
    }

    /**
     * @return orders which are open again
     */
    public List<SimpleOrder> orders() {
        return releases.stream().map(Release::order).toList();
    }

    /**
     * @return workers with returned items
     */
    public Set<UUID> workers() {
        Set<UUID> workers = new HashSet<>();
        releases.forEach(release -> workers.addAll(release.returned().workers()));
        return Set.copyOf(workers);
    }

    /**
     * One released order.
     *
     * @param order    the order, which is open again
     * @param returned items returned to its workers
     */
    public record Release(SimpleOrder order, ReturnedItems returned) {}
}
