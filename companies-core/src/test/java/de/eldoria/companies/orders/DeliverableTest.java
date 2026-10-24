/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.ItemMatchSettings;
import de.eldoria.companies.data.wrapper.order.DeliveredStack;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@SuppressWarnings("UnstableApiUsage")
class DeliverableTest {
    private static final ItemMatcher MATCHER =
            ItemMatcher.of(new ItemMatchSettings(List.of(), false), Logger.getAnonymousLogger());

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

    private static OrderContent content(int line, ItemStack template, int amount, int delivered) {
        var content = new OrderContent(line, template, false, amount, 10.0);
        content.deliveries(
                delivered == 0
                        ? List.of()
                        : List.of(new DeliveredStack(0, UUID.randomUUID(), template.clone(), delivered)));
        return content;
    }

    private static ComponentStack stack(Material material, int amount) {
        var stack = new ComponentStack(material);
        stack.setAmount(amount);
        return stack;
    }

    private static ComponentStack book(int sharpness) {
        var stack = new ComponentStack(Material.ENCHANTED_BOOK);
        stack.setData(
                DataComponentTypes.STORED_ENCHANTMENTS,
                ItemEnchantments.itemEnchantments()
                        .add(Enchantment.SHARPNESS, sharpness)
                        .build());
        return stack;
    }

    private static List<ItemStack> items(ItemStack... stacks) {
        return new ArrayList<>(List.of(stacks));
    }

    @Test
    void matchingItemsCountForMaterialLines() {
        var enchanted = stack(Material.STONE, 4);
        enchanted.setData(
                DataComponentTypes.ENCHANTMENTS,
                ItemEnchantments.itemEnchantments()
                        .add(Enchantment.SHARPNESS, 1)
                        .build());
        var order = order(content(0, new ComponentStack(Material.STONE), 20, 0));

        var amount = Deliverable.amount(
                MATCHER, items(stack(Material.STONE, 10), stack(Material.DIRT, 5), enchanted), order);

        Assertions.assertEquals(14, amount);
    }

    @Test
    void exactLinesCountOnlyMatchingItems() {
        var order = order(content(0, book(5), 3, 0));

        Assertions.assertEquals(1, Deliverable.amount(MATCHER, items(book(4), book(5)), order));
    }

    @Test
    void everyItemCountsForOneLineOnly() {
        var order = order(
                content(0, new ComponentStack(Material.STONE), 5, 0),
                content(1, new ComponentStack(Material.STONE), 5, 0));

        Assertions.assertEquals(7, Deliverable.amount(MATCHER, items(stack(Material.STONE, 7)), order));
    }

    @Test
    void amountIsLimitedByWhatIsMissing() {
        var order = order(
                content(0, new ComponentStack(Material.STONE), 10, 4),
                content(1, new ComponentStack(Material.DIRT), 5, 0));

        Assertions.assertEquals(6, Deliverable.amount(MATCHER, items(stack(Material.STONE, 64)), order));
        Assertions.assertEquals(
                9, Deliverable.amount(MATCHER, items(stack(Material.STONE, 64), stack(Material.DIRT, 3)), order));
        Assertions.assertEquals(0, Deliverable.amount(MATCHER, items(stack(Material.GRAVEL, 64)), order));
    }
}
