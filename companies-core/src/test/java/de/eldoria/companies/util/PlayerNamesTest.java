/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerNamesTest {
    private static final UUID ADA = UUID.fromString("71646a0b-21ff-316a-84a2-681b0e1b7934");

    @Test
    void aKnownPlayerKeepsTheServersNameWithoutAskingTheCache() {
        var asked = new AtomicInteger();

        var name = PlayerNames.of(player(ADA, "Ada"), id -> {
            asked.incrementAndGet();
            return profile(id, Map.of());
        });

        assertEquals("Ada", name);
        assertEquals(0, asked.get());
    }

    @Test
    void aPlayerWhoNeverJoinedIsNamedFromTheUserCache() {
        assertEquals("Demo_Ada", PlayerNames.of(player(ADA, null), id -> profile(id, Map.of(ADA, "Demo_Ada"))));
    }

    @Test
    void aPlayerTheCacheDoesNotKnowHasNoName() {
        assertNull(PlayerNames.of(player(ADA, null), id -> profile(id, Map.of())));
    }

    private static OfflinePlayer player(UUID id, @Nullable String name) {
        return (OfflinePlayer) Proxy.newProxyInstance(
                OfflinePlayer.class.getClassLoader(),
                new Class<?>[] {OfflinePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> id;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static PlayerProfile profile(UUID id, Map<UUID, String> cache) {
        var name = new AtomicReference<String>();
        return (PlayerProfile) Proxy.newProxyInstance(
                PlayerProfile.class.getClassLoader(),
                new Class<?>[] {PlayerProfile.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "completeFromCache" -> {
                        name.set(cache.get(id));
                        yield name.get() != null;
                    }
                    case "getName" -> name.get();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
