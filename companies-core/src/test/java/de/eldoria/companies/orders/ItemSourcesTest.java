/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@SuppressWarnings("UnstableApiUsage")
class ItemSourcesTest {
    private static ServerMock server;

    @BeforeAll
    static void startServer() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private static ItemMatcher matcher(boolean allowDamaged) {
        return ItemMatcher.of(new ItemMatchSettings(List.of(), allowDamaged), Logger.getAnonymousLogger());
    }

    private static ComponentStack stack(Material material, int amount) {
        var stack = new ComponentStack(material);
        stack.setAmount(amount);
        return stack;
    }

    private static ComponentStack sword() {
        var stack = new ComponentStack(Material.DIAMOND_SWORD);
        stack.setData(DataComponentTypes.MAX_DAMAGE, 100);
        return stack;
    }

    private static ComponentStack enchantedSword() {
        var stack = sword();
        stack.setData(
                DataComponentTypes.ENCHANTMENTS,
                ItemEnchantments.itemEnchantments()
                        .add(Enchantment.SHARPNESS, 5)
                        .build());
        return stack;
    }

    private static ComponentStack damagedSword() {
        var stack = sword();
        stack.setData(DataComponentTypes.DAMAGE, 50);
        return stack;
    }

    private static ComponentStack shulker(List<ItemStack> content) {
        var stack = new ComponentStack(Material.SHULKER_BOX);
        stack.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(content));
        return stack;
    }

    @Test
    void collectsStorageAndTheShulkerInHandButNotTheShulkerItself() {
        var player = server.addPlayer();
        var inventory = player.getInventory();
        inventory.setHeldItemSlot(0);
        inventory.setItem(0, shulker(List.of(stack(Material.DIRT, 20), ItemStack.empty(), stack(Material.STONE, 3))));
        inventory.setItem(5, stack(Material.STONE, 10));

        var slots = ItemSources.collect(player);

        Assertions.assertEquals(3, slots.size());
        Assertions.assertTrue(slots.stream().noneMatch(slot -> slot.stack().getType() == Material.SHULKER_BOX));
        Assertions.assertEquals(
                List.of(Material.STONE, Material.DIRT, Material.STONE),
                slots.stream().map(slot -> slot.stack().getType()).toList());
    }

    @Test
    void takingFromTheShulkerChangesItsContentInPlace() {
        var player = server.addPlayer();
        var inventory = player.getInventory();
        inventory.setHeldItemSlot(0);
        inventory.setItem(0, shulker(List.of(stack(Material.DIRT, 20), ItemStack.empty(), stack(Material.STONE, 3))));

        var parts =
                ItemSources.take(ItemSources.collect(player), 2, new ComponentStack(Material.STONE), matcher(false), 2);

        Assertions.assertEquals(1, parts.size());
        Assertions.assertEquals(2, parts.get(0).line());
        Assertions.assertEquals(2, parts.get(0).amount());
        Assertions.assertEquals(1, parts.get(0).stack().getAmount());
        var hand = inventory.getItemInMainHand();
        Assertions.assertEquals(Material.SHULKER_BOX, hand.getType());
        var content = hand.getData(DataComponentTypes.CONTAINER).contents();
        Assertions.assertEquals(3, content.size());
        Assertions.assertEquals(20, content.get(0).getAmount());
        Assertions.assertEquals(Material.STONE, content.get(2).getType());
        Assertions.assertEquals(1, content.get(2).getAmount());
    }

    @Test
    void takingFromAStorageSlotChangesTheInventory() {
        var player = server.addPlayer();
        var inventory = player.getInventory();
        inventory.setItem(5, stack(Material.STONE, 10));
        inventory.setItem(7, stack(Material.DIRT, 2));

        var slots = ItemSources.collect(player);
        slots.get(0).take(4);
        slots.get(1).take(2);

        Assertions.assertEquals(6, inventory.getItem(5).getAmount());
        var emptied = inventory.getItem(7);
        Assertions.assertTrue(emptied == null || emptied.getType().isAir());
    }

    @Test
    void plainUndamagedItemsAreTakenFirst() {
        var slots = slots(enchantedSword(), damagedSword(), sword());

        var parts = ItemSources.take(slots, 0, sword(), matcher(true), 3);

        Assertions.assertEquals(3, parts.size());
        Assertions.assertTrue(parts.get(0).stack().isSimilar(sword()));
        Assertions.assertTrue(parts.get(1).stack().isSimilar(damagedSword()));
        Assertions.assertTrue(parts.get(2).stack().isSimilar(enchantedSword()));
    }

    @Test
    void onlyAsManyAsTheLimitAreTaken() {
        var slots = slots(enchantedSword(), sword());

        var parts = ItemSources.take(slots, 0, sword(), matcher(false), 1);

        Assertions.assertEquals(1, parts.size());
        Assertions.assertTrue(parts.get(0).stack().isSimilar(sword()));
        Assertions.assertEquals(1, slots.get(0).stack().getAmount());
        Assertions.assertTrue(slots.get(1).stack().isEmpty());
    }

    @Test
    void damagedItemsAreLeftWithoutPermission() {
        var slots = slots(damagedSword(), sword());

        var parts = ItemSources.take(slots, 0, sword(), matcher(false), 5);

        Assertions.assertEquals(1, parts.size());
        Assertions.assertEquals(1, parts.get(0).amount());
        Assertions.assertEquals(1, slots.get(0).stack().getAmount());
    }

    @Test
    void equalItemsFromSeveralSlotsBecomeOnePart() {
        var slots = slots(stack(Material.STONE, 5), stack(Material.DIRT, 5), stack(Material.STONE, 7));

        var parts = ItemSources.take(slots, 1, new ComponentStack(Material.STONE), matcher(false), 64);

        Assertions.assertEquals(List.of(new DeliveryPart(1, parts.get(0).stack(), 12)), parts);
        Assertions.assertEquals(5, slots.get(1).stack().getAmount());
    }

    private static List<ItemSlot> slots(ItemStack... stacks) {
        List<ItemSlot> slots = new ArrayList<>();
        for (var stack : stacks) {
            slots.add(new ListSlot(stack));
        }
        return slots;
    }

    /**
     * A slot holding one stack, standing in for an inventory slot.
     */
    private static final class ListSlot implements ItemSlot {
        private ItemStack stack;

        ListSlot(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public ItemStack stack() {
            return stack;
        }

        @Override
        public void take(int amount) {
            var left = stack.clone();
            left.setAmount(stack.getAmount() - amount);
            stack = left.getAmount() <= 0 ? ItemStack.empty() : left;
        }
    }
}
