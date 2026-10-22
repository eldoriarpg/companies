/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.ContentPart;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class DeliverableTest {
    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private static FullOrder order(OrderContent... contents) {
        return new FullOrder(
                1,
                UUID.randomUUID(),
                "order",
                LocalDateTime.now(),
                0,
                LocalDateTime.now(),
                OrderState.UNCLAIMED,
                0.0,
                0,
                0,
                List.of(contents));
    }

    private static OrderContent content(Material material, int amount, int delivered) {
        var content = new OrderContent(new ItemStack(material), amount, 10.0);
        content.parts(delivered == 0 ? List.of() : List.of(new ContentPart(UUID.randomUUID(), delivered)));
        return content;
    }

    @Test
    void plainItemsAreCountedPerMaterial() {
        var named = new ItemStack(Material.STONE, 5);
        var meta = named.getItemMeta();
        meta.displayName(Component.text("Special"));
        named.setItemMeta(meta);

        var available = Deliverable.available(
                new ItemStack[] {new ItemStack(Material.STONE, 10), null, new ItemStack(Material.STONE, 3), named},
                new ItemStack(Material.AIR));

        Assertions.assertEquals(Map.of(Material.STONE, 13), available);
    }

    @Test
    void shulkerBoxInTheMainHandCounts() {
        var shulker = new ItemStack(Material.SHULKER_BOX);
        var meta = (BlockStateMeta) shulker.getItemMeta();
        var box = (ShulkerBox) meta.getBlockState();
        box.getInventory().addItem(new ItemStack(Material.DIRT, 20));
        meta.setBlockState(box);
        shulker.setItemMeta(meta);

        var available = Deliverable.available(new ItemStack[] {new ItemStack(Material.DIRT, 4)}, shulker);

        Assertions.assertEquals(24, available.get(Material.DIRT));
    }

    @Test
    void amountIsLimitedByWhatIsMissing() {
        var order = order(content(Material.STONE, 10, 4), content(Material.DIRT, 5, 0));

        Assertions.assertEquals(6, Deliverable.amount(Map.of(Material.STONE, 64), order));
        Assertions.assertEquals(9, Deliverable.amount(Map.of(Material.STONE, 64, Material.DIRT, 3), order));
        Assertions.assertEquals(0, Deliverable.amount(Map.of(Material.GRAVEL, 64), order));
    }
}
