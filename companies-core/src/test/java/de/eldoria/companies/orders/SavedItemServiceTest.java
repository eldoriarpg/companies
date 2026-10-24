/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.configuration.elements.ordersettings.SavedItemSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteSavedItemData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.saved.SavedItem;
import de.eldoria.companies.services.ActionResult;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Saving items to order them again, against SQLite.
 */
class SavedItemServiceTest {
    @TempDir
    Path directory;

    private final UUID owner = UUID.randomUUID();
    private ExecutorService executor;
    private SavedItemService service;
    private int max = 2;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newSingleThreadExecutor();
        service = new SavedItemService(
                new SqLiteSavedItemData(SqLiteTestDatabase.mapper(), executor),
                item -> item.getType() == Material.BEDROCK
                        ? Optional.empty()
                        : Optional.of(new OrderDraftService.RequestedItem(item.asOne(), item.hasItemMeta())),
                () -> new SavedItemSettings(max));
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
        MockBukkit.unmock();
    }

    private static ItemStack swords() {
        var sword = new ItemStack(Material.DIAMOND_SWORD, 3);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        return sword;
    }

    private String refusal(ActionResult<?> result) {
        return assertInstanceOf(ActionResult.Refused.class, result).failure().localeKey();
    }

    @Test
    void anItemIsSavedAsOneTemplateUnderItsOrTheMaterialsName() {
        var named = assertInstanceOf(
                        ActionResult.Done.class,
                        service.save(owner, swords(), Optional.of("blade")).join())
                .value();
        var saved = (SavedItem) named;
        assertEquals(1, saved.stack().getAmount());
        assertEquals(5, saved.stack().getEnchantmentLevel(Enchantment.SHARPNESS));

        service.save(owner, new ItemStack(Material.OAK_LOG), Optional.empty()).join();
        assertEquals(
                List.of("blade", "oak_log"),
                service.items(owner).join().stream().map(SavedItem::name).toList());
    }

    @Test
    void namesItemsAndTheCapAreChecked() {
        assertEquals(
                "order.draft.error.emptyHand",
                refusal(service.save(owner, ItemStack.empty(), Optional.empty()).join()));
        assertEquals(
                "order.draft.error.notOrderable",
                refusal(service.save(owner, new ItemStack(Material.BEDROCK), Optional.empty())
                        .join()));
        assertEquals(
                "order.items.error.invalidName",
                refusal(service.save(owner, swords(), Optional.of("two words")).join()));
        service.save(owner, swords(), Optional.of("a")).join();
        assertEquals(
                "order.items.error.nameTaken",
                refusal(service.save(owner, swords(), Optional.of("a")).join()));
        service.save(owner, swords(), Optional.of("b")).join();
        assertEquals(
                "order.items.error.limit",
                refusal(service.save(owner, swords(), Optional.of("c")).join()));

        max = 0;
        assertEquals(
                "order.items.error.disabled",
                refusal(service.save(owner, swords(), Optional.of("d")).join()));
    }

    @Test
    void anItemIsFoundAndRemovedByName() {
        service.save(owner, swords(), Optional.of("blade")).join();

        assertInstanceOf(ActionResult.Done.class, service.item(owner, "blade").join());
        assertInstanceOf(ActionResult.Done.class, service.remove(owner, "blade").join());
        assertEquals(
                "order.items.error.unknown",
                refusal(service.remove(owner, "blade").join()));
        assertEquals(
                "order.items.error.unknown",
                refusal(service.item(owner, "blade").join()));
    }
}
