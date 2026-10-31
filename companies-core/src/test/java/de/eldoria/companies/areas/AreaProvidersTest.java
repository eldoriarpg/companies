/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.components.area.AreaBounds;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.AreaProvider;
import de.eldoria.companies.configuration.elements.areas.AreaSettings;
import org.bukkit.Location;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry of area providers: asked in the configured order, switched-off and failing providers skipped, a
 * provider registered again replacing the one before.
 */
class AreaProvidersTest {
    private final AreaSettings settings = new AreaSettings();
    private final AreaProviders providers = new AreaProviders(Logger.getAnonymousLogger());
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void providersAreAskedInTheConfiguredOrderAndUnlistedOnesLast() {
        providers.settings(() -> settings);
        providers.register(plugin, new Fixed("towny", "town"));
        providers.register(plugin, new Fixed("worldguard", "world/spawn"));
        providers.register(plugin, new Fixed("plotsquared", "plots|1;1"));

        assertEquals(List.of("plotsquared", "worldguard", "towny"), providers.providers());
        assertEquals(new AreaRef("plotsquared", "plots|1;1"), providers.at(null).orElseThrow());
    }

    @Test
    void aSwitchedOffProviderIsSkippedAndCanNotBeFound() {
        settings.providers(Map.of("plotsquared", false));
        providers.settings(() -> settings);
        providers.register(plugin, new Fixed("plotsquared", "plots|1;1"));
        providers.register(plugin, new Fixed("worldguard", "world/spawn"));

        assertEquals(
                new AreaRef("worldguard", "world/spawn"), providers.at(null).orElseThrow());
        assertFalse(providers.available("plotsquared"));
        assertTrue(providers.available("WorldGuard"));
    }

    @Test
    void aFailingProviderIsSkippedAndAProviderRegisteredAgainReplacesTheOld() {
        providers.settings(() -> settings);
        providers.register(plugin, new Fixed("plotsquared", null));
        providers.register(plugin, new Fixed("worldguard", "world/spawn"));
        providers.register(plugin, new Fixed("worldguard", "world/market"));

        assertEquals(
                new AreaRef("worldguard", "world/market"), providers.at(null).orElseThrow());
        assertEquals(2, providers.providers().size());
        providers.unregister("worldguard");
        assertTrue(providers.at(null).isEmpty());
    }

    @Test
    void theProvidersOfADisabledPluginAreDropped() {
        providers.settings(() -> settings);
        var other = MockBukkit.createMockPlugin("Towny");
        providers.register(plugin, new Fixed("plotsquared", "plots|1;1"));
        providers.register(other, new Fixed("towny", "town"));

        providers.onDisable(new PluginDisableEvent(other));

        assertEquals(List.of("plotsquared"), providers.providers());
    }

    @Test
    void namesAreMatchedIgnoringCaseAndAFailingCallFallsBack() {
        settings.providers(Map.of("PlotSquared", false));
        providers.settings(() -> settings);
        providers.register(plugin, new Fixed("plotsquared", "plots|1;1"));
        providers.register(plugin, new Fixed("worldguard", null));

        assertTrue(providers.providers().stream().noneMatch("plotsquared"::equals));
        assertEquals(
                "fallback",
                providers.call("worldguard", provider -> provider.at(null).orElseThrow(), "fallback"));
    }

    /**
     * A provider that knows one area everywhere, or fails on every lookup when it knows none.
     */
    private record Fixed(String id, String area) implements AreaProvider {
        @Override
        public Set<AreaCapability> capabilities() {
            return Set.of();
        }

        @Override
        public List<String> areasAt(Location location) {
            if (area == null) throw new IllegalStateException("the plugin failed");
            return List.of(area);
        }

        @Override
        public boolean exists(String ref) {
            return ref.equals(area);
        }

        @Override
        public Set<UUID> owners(String ref) {
            return Set.of();
        }

        @Override
        public Optional<AreaBounds> bounds(String ref) {
            return Optional.empty();
        }

        @Override
        public String name(String ref) {
            return ref;
        }
    }
}
