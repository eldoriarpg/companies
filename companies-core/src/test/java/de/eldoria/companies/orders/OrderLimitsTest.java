/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.configuration.elements.UserSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class OrderLimitsTest {
    private ServerMock server;
    private PluginMock plugin;
    private final UserSettings settings = new UserSettings();
    private final OrderLimits limits = new OrderLimits(null, () -> settings);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        settings.maxOrders(7);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void configuredLimitAppliesWithoutPermission() {
        var player = server.addPlayer();

        Assertions.assertEquals(7, limits.limit(player));
    }

    @Test
    void configuredLimitFollowsTheSettings() {
        var player = server.addPlayer();
        settings.maxOrders(3);

        Assertions.assertEquals(3, limits.limit(player));
    }

    @Test
    void highestLimitPermissionOverridesTheConfiguredLimit() {
        var player = server.addPlayer();
        player.addAttachment(plugin, "companies.orders.limit.2", true);
        player.addAttachment(plugin, "companies.orders.limit.12", true);

        Assertions.assertEquals(12, limits.limit(player));
    }

    @Test
    void permissionCanLowerTheLimit() {
        var player = server.addPlayer();
        player.addAttachment(plugin, "companies.orders.limit.1", true);

        Assertions.assertEquals(1, limits.limit(player));
    }

    @Test
    void usageIsReachedAtTheLimit() {
        Assertions.assertFalse(new OrderLimits.Usage(6, 7).reached());
        Assertions.assertTrue(new OrderLimits.Usage(7, 7).reached());
        Assertions.assertTrue(new OrderLimits.Usage(8, 7).reached());
    }
}
