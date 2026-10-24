/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeCachesTest {
    private final NodeCaches caches = new NodeCaches(Logger.getLogger("test"));
    private final List<String> dropped = new ArrayList<>();

    @Test
    void everyCacheOfANameDropsTheKey() {
        caches.register(NodeCaches.COMPANY, key -> dropped.add("a" + key.orElse("*")));
        caches.register(NodeCaches.COMPANY, key -> dropped.add("b" + key.orElse("*")));
        caches.register(NodeCaches.ORDER, key -> dropped.add("order"));

        caches.invalidate(NodeCaches.COMPANY, Optional.of("4"));
        caches.invalidate("unknown", Optional.empty());

        assertEquals(List.of("a4", "b4"), dropped);
    }

    @Test
    void aFailingCacheDoesNotStopTheOthers() {
        caches.register(NodeCaches.PRICES, key -> {
            throw new IllegalStateException("broken");
        });
        caches.register(NodeCaches.PRICES, key -> dropped.add("prices"));

        caches.invalidate(NodeCaches.PRICES, Optional.empty());

        assertEquals(List.of("prices"), dropped);
    }

    @Test
    void clearDropsEverything() {
        caches.register(NodeCaches.COMPANY, key -> dropped.add("company" + key.orElse("*")));
        caches.register(NodeCaches.BOARDS, key -> dropped.add("boards" + key.orElse("*")));

        caches.clear();

        dropped.sort(String::compareTo);
        assertEquals(List.of("boards*", "company*"), dropped);
    }
}
