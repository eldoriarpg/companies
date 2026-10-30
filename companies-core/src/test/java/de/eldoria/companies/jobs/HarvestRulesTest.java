/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a harvest job lets members break and what goes into the stock.
 */
class HarvestRulesTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void cropsAreSortedByHowTheyAreHarvested() {
        assertEquals(HarvestRules.Crop.FIELD, HarvestRules.crop(Material.WHEAT));
        assertEquals(HarvestRules.Crop.FIELD, HarvestRules.crop(Material.COCOA));
        assertEquals(HarvestRules.Crop.STACKED, HarvestRules.crop(Material.SUGAR_CANE));
        assertEquals(HarvestRules.Crop.FRUIT, HarvestRules.crop(Material.PUMPKIN));
        assertEquals(HarvestRules.Crop.NONE, HarvestRules.crop(Material.MELON_STEM));
        assertEquals(HarvestRules.Crop.NONE, HarvestRules.crop(Material.FARMLAND));
    }

    @Test
    void aFieldCropKeepsOneSeedBackForTheReplant() {
        var items = HarvestRules.yield(
                Material.WHEAT, List.of(new ItemStack(Material.WHEAT, 1), new ItemStack(Material.WHEAT_SEEDS, 2)));

        assertEquals(Map.of("WHEAT", 1, "WHEAT_SEEDS", 1), items);
        assertEquals(
                Map.of("CARROT", 2), HarvestRules.yield(Material.CARROTS, List.of(new ItemStack(Material.CARROT, 3))));
        assertEquals(Map.of(), HarvestRules.yield(Material.POTATOES, List.of(new ItemStack(Material.POTATO, 1))));
    }

    @Test
    void aFruitKeepsNothingBack() {
        assertEquals(
                Map.of("MELON_SLICE", 5),
                HarvestRules.yield(Material.MELON, List.of(new ItemStack(Material.MELON_SLICE, 5))));
    }
}
