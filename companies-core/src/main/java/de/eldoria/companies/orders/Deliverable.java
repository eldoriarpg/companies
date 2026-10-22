/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Works out how much of an order a player could deliver with the items they carry.
 * <p>
 * Plain items in the inventory count, and so do plain items inside a shulker box held in the main hand. Items with a
 * custom name, enchantments or other data do not count, since a delivery would not accept them.
 */
public final class Deliverable {
    private Deliverable() {}

    /**
     * Counts the plain items a player carries. Has to be called on the main thread.
     *
     * @param player player to count the items of
     * @return amount per material
     */
    public static Map<Material, Integer> available(Player player) {
        var inventory = player.getInventory();
        return available(inventory.getStorageContents(), inventory.getItemInMainHand());
    }

    /**
     * Counts plain items in storage slots and in the shulker box held in the main hand.
     *
     * @param storage  storage slots
     * @param mainHand item in the main hand, which may be a shulker box
     * @return amount per material
     */
    public static Map<Material, Integer> available(ItemStack[] storage, ItemStack mainHand) {
        Map<Material, Integer> available = new EnumMap<>(Material.class);
        Stream.concat(Arrays.stream(storage), shulkerContents(mainHand))
                .filter(Objects::nonNull)
                .filter(Deliverable::isPlain)
                .forEach(stack -> available.merge(stack.getType(), stack.getAmount(), Integer::sum));
        return available;
    }

    /**
     * The amount of items of an order the player could deliver.
     *
     * @param available items the player carries, per material
     * @param order     order to fill
     * @return sum over all contents of what is missing and available
     */
    public static int amount(Map<Material, Integer> available, FullOrder order) {
        return order.contents().stream()
                .mapToInt(content ->
                        Math.min(Math.max(0, content.missing()), available.getOrDefault(content.material(), 0)))
                .sum();
    }

    private static Stream<ItemStack> shulkerContents(ItemStack stack) {
        if (stack == null
                || !(stack.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            return Stream.empty();
        }
        return Arrays.stream(box.getInventory().getContents());
    }

    private static boolean isPlain(ItemStack stack) {
        return !stack.getType().isAir() && stack.isSimilar(new ItemStack(stack.getType()));
    }
}
