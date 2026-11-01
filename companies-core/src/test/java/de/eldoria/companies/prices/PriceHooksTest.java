/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import de.eldoria.companies.components.price.PriceSource;
import org.bukkit.event.server.PluginDisableEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loading Companies' own price sources: only for enabled shop plugins, in the name of the shop plugin, and a source
 * that can not be loaded skipped.
 */
class PriceHooksTest {
    private PriceSources sources;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        sources = new PriceSources(
                new TradeLog(new MemoryPriceData(), () -> LocalDate.EPOCH, Logger.getAnonymousLogger()),
                Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void sourcesOfEnabledShopPluginsAreRegisteredInTheirNameAndBrokenOnesSkipped() {
        var companies = MockBukkit.createMockPlugin("Companies");
        var shop = MockBukkit.createMockPlugin("Shop");
        MockBukkit.createMockPlugin("Broken");
        var known = new LinkedHashMap<String, String>();
        known.put("Shop", Source.class.getName());
        known.put("Broken", "de.eldoria.companies.prices.Missing");
        known.put("Absent", Source.class.getName());

        PriceHooks.load(companies, sources, known);

        assertEquals(List.of("source"), sources.sources());
        sources.onDisable(new PluginDisableEvent(shop));
        assertTrue(sources.sources().isEmpty());
    }

    /**
     * A source loaded by its class name.
     */
    public static final class Source implements PriceSource {
        @Override
        public String id() {
            return "source";
        }

        @Override
        public String name() {
            return "Source";
        }
    }
}
