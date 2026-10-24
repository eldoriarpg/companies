/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.eldoria.companies.data.wrapper.saved.SaveResult;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every dialect of the saved items does. The database is started once per dialect and emptied before each test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class SavedItemDataContract {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 10, 0);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private DataSource dataSource;
    private ASavedItemData data;
    private final UUID owner = UUID.randomUUID();

    /**
     * @return a migrated database
     */
    protected abstract DataSource start() throws Exception;

    /**
     * Stops the database.
     */
    protected abstract void stop();

    /**
     * @param executor executor of the futures
     * @return the dialect's saved items
     */
    protected abstract ASavedItemData data(ExecutorService executor);

    @BeforeAll
    void startDatabase() throws Exception {
        MockBukkit.mock();
        dataSource = start();
    }

    @BeforeEach
    void setUp() {
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource).build());
        query("DELETE FROM saved_items").single().delete();
        data = data(executor);
    }

    @AfterAll
    void stopDatabase() {
        stop();
        executor.shutdown();
        MockBukkit.unmock();
    }

    private static ItemStack sword() {
        var sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        return sword;
    }

    @Test
    void anItemIsSavedReadByNameAndIdAndRemoved() {
        assertEquals(
                SaveResult.SAVED,
                data.submitItem(owner, "Blade", sword(), 3, NOW).join());

        var saved = data.retrieveItem(owner, "Blade").join().orElseThrow();
        assertEquals(5, saved.stack().getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals(NOW, saved.created());
        assertEquals(
                saved.name(),
                data.retrieveItem(owner, saved.id()).join().orElseThrow().name());
        assertTrue(data.retrieveItem(UUID.randomUUID(), saved.id()).join().isEmpty());

        assertTrue(data.submitRemoval(owner, "Blade").join());
        assertFalse(data.submitRemoval(owner, "Blade").join());
        assertTrue(data.retrieveItems(owner).join().isEmpty());
    }

    @Test
    void namesAreUniquePerPlayerAndTheCapHolds() {
        assertEquals(
                SaveResult.SAVED, data.submitItem(owner, "B", sword(), 2, NOW).join());
        assertEquals(
                SaveResult.NAME_TAKEN,
                data.submitItem(owner, "B", sword(), 2, NOW).join());
        assertEquals(
                SaveResult.SAVED,
                data.submitItem(owner, "A", new ItemStack(Material.STONE), 2, NOW)
                        .join());
        assertEquals(
                SaveResult.LIMIT_REACHED,
                data.submitItem(owner, "C", sword(), 2, NOW).join());
        assertEquals(
                SaveResult.SAVED,
                data.submitItem(UUID.randomUUID(), "B", sword(), 2, NOW).join());

        assertEquals(
                List.of("A", "B"),
                data.retrieveItems(owner).join().stream()
                        .map(item -> item.name())
                        .toList());
    }
}
