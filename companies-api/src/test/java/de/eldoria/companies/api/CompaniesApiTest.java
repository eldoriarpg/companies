/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.testing.Stubs;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompaniesApiTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void getFailsWhileNothingIsRegistered() {
        assertThrows(IllegalStateException.class, CompaniesApi::get);
    }

    @Test
    void getReturnsTheRegisteredService() {
        var api = Stubs.of(CompaniesApi.class, Map.of());
        var plugin = MockBukkit.createMockPlugin();

        server.getServicesManager().register(CompaniesApi.class, api, plugin, ServicePriority.Normal);

        assertSame(api, CompaniesApi.get());
    }
}
