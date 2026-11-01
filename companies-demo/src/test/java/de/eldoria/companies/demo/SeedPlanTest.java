/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeedPlanTest {
    @BeforeEach
    void startServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void readsCompaniesAndOrdersAndReportsWhatItLeavesOut() throws InvalidConfigurationException {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
                seed:
                  companies:
                    - name: Stonebridge
                      owner: Demo_Ada
                      members: [Demo_Ben, Demo_Cleo]
                    - name: Nameless
                  orders:
                    - owner: Demo_Dan
                      name: Building stone
                      lines:
                        - {material: stone, amount: 256, price: 0.5}
                        - {material: not_an_item, amount: 1, price: 1}
                    - owner: Demo_Eve
                      name: Nothing
                      lines: []
                  market:
                    days: 14
                    prices:
                      stone: 0.4
                      iron_ingot: 3
                      nothing: 1
                """);
        List<String> problems = new ArrayList<>();

        var plan = SeedPlan.read(yaml.getConfigurationSection("seed"), problems::add);

        assertEquals(
                List.of(new SeedPlan.Company("Stonebridge", "Demo_Ada", List.of("Demo_Ben", "Demo_Cleo"))),
                plan.companies());
        assertEquals(
                List.of(new SeedPlan.Order(
                        "Demo_Dan", "Building stone", List.of(new SeedPlan.Line(Material.STONE, 256, 0.5)))),
                plan.orders());
        assertEquals(Map.of("STONE", 0.4, "IRON_INGOT", 3.0), plan.market());
        assertEquals(14, plan.marketDays());
        assertEquals(4, problems.size(), problems::toString);
    }
}
