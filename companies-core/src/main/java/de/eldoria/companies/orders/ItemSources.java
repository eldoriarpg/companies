/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.IOrderContent;
import de.eldoria.companies.components.order.ItemCriteria;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Finds the items a worker can deliver and takes them out. Used on the main thread only.
 * <p>
 * Items come from the storage slots of the player and from a shulker box held in the main hand. The held shulker box
 * itself is never taken.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ItemSources {
    private ItemSources() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Collects the slots a player can deliver from.
     *
     * @param player worker
     * @return the non-empty storage slots, then the non-empty slots of a shulker box in the main hand
     */
    public static List<ItemSlot> collect(Player player) {
        return collect(player.getInventory());
    }

    /**
     * Collects the slots of an inventory items can be delivered from.
     *
     * @param inventory inventory of the worker
     * @return the non-empty storage slots, then the non-empty slots of a shulker box in the main hand
     */
    public static List<ItemSlot> collect(PlayerInventory inventory) {
        List<ItemSlot> slots = new ArrayList<>();
        var hand = inventory.getItemInMainHand();
        var holdsShulker = isShulkerBox(hand);
        var storage = Objects.requireNonNullElse(inventory.getStorageContents(), new ItemStack[0]);
        for (var index = 0; index < storage.length; index++) {
            if (holdsShulker && index == inventory.getHeldItemSlot()) continue;
            if (isEmpty(storage[index])) continue;
            slots.add(new StorageSlot(inventory, index));
        }
        if (holdsShulker) slots.addAll(ShulkerContents.of(inventory).slots());
        return slots;
    }

    /**
     * Takes up to an amount of items matching a line from the slots.
     * <p>
     * Items with the fewest extra components go first, then the least damaged ones, so plain undamaged items are
     * delivered before enchanted or worn ones. The taken items are grouped into one part per distinct item, in the
     * order they were taken.
     *
     * @param slots    slots to take from; they are changed
     * @param line     line of the order
     * @param template requested item of the line
     * @param matcher  decides which items match
     * @param limit    the most items to take
     * @return the taken items per distinct item
     */
    public static List<DeliveryPart> take(
            List<ItemSlot> slots, int line, ItemStack template, ItemMatcher matcher, int limit) {
        return take(slots, line, template, null, matcher, limit);
    }

    /**
     * Takes up to an amount of items matching a line of any kind from the slots, as
     * {@link #take(List, int, ItemStack, ItemMatcher, int)} does.
     *
     * @param slots    slots to take from; they are changed
     * @param line     line of the order
     * @param template requested item of the line
     * @param criteria criteria of a criteria line, null for material and exact lines
     * @param matcher  decides which items match
     * @param limit    the most items to take
     * @return the taken items per distinct item
     */
    public static List<DeliveryPart> take(
            List<ItemSlot> slots,
            int line,
            ItemStack template,
            @Nullable ItemCriteria criteria,
            ItemMatcher matcher,
            int limit) {
        return take(slots, line, template, criteria, matcher, limit, stack -> false);
    }

    /**
     * Takes items for all lines of an order that still miss items, as a delivery of everything the worker carries
     * does. Exact lines are filled first, then material lines, then criteria lines, see
     * {@link DeliveryOrder#narrowFirst(List)}. Among items a line ranks alike, it takes those no later line accepts
     * first, so they stay for the lines that need them.
     *
     * @param slots   slots to take from; they are changed
     * @param lines   lines of the order
     * @param matcher decides which items match
     * @return the taken items, per line in the order the lines were filled
     */
    public static List<DeliveryPart> fill(
            List<ItemSlot> slots, List<? extends IOrderContent> lines, ItemMatcher matcher) {
        var open = DeliveryOrder.narrowFirst(lines).stream()
                .filter(line -> line.missing() > 0)
                .toList();
        List<DeliveryPart> parts = new ArrayList<>();
        for (var index = 0; index < open.size(); index++) {
            var line = open.get(index);
            var later = open.subList(index + 1, open.size());
            parts.addAll(take(
                    slots,
                    line.line(),
                    line.stack(),
                    line.criteria().orElse(null),
                    matcher,
                    line.missing(),
                    stack -> later.stream().anyMatch(other -> matcher.matches(other, stack))));
        }
        return parts;
    }

    /**
     * Slots holding copies of items, to work out a delivery without changing anything.
     *
     * @param stacks the items
     * @return one slot per item
     */
    public static List<ItemSlot> copies(List<ItemStack> stacks) {
        List<ItemSlot> slots = new ArrayList<>();
        for (var stack : stacks) slots.add(new CopySlot(stack.clone()));
        return slots;
    }

    private static List<DeliveryPart> take(
            List<ItemSlot> slots,
            int line,
            ItemStack template,
            @Nullable ItemCriteria criteria,
            ItemMatcher matcher,
            int limit,
            Predicate<ItemStack> wantedLater) {
        var candidates = slots.stream()
                .filter(slot -> !isEmpty(slot.stack()))
                .filter(slot -> matcher.matches(template, criteria, slot.stack()))
                .map(slot -> new Candidate(
                        slot,
                        matcher.extras(template, criteria, slot.stack()),
                        matcher.damage(slot.stack()),
                        wantedLater.test(slot.stack())))
                .sorted(Comparator.comparingInt(Candidate::extras)
                        .thenComparingDouble(Candidate::damage)
                        .thenComparing(Candidate::wantedLater))
                .toList();
        List<DeliveryPart> parts = new ArrayList<>();
        var remaining = limit;
        for (var candidate : candidates) {
            if (remaining <= 0) break;
            var stack = candidate.slot().stack();
            var taken = Math.min(remaining, stack.getAmount());
            var single = stack.clone();
            single.setAmount(1);
            candidate.slot().take(taken);
            remaining -= taken;
            add(parts, new DeliveryPart(line, single, taken));
        }
        return parts;
    }

    /**
     * Copies what the slots hold, for counting off the main thread.
     *
     * @param slots slots to copy
     * @return copies of the items
     */
    public static List<ItemStack> snapshot(List<ItemSlot> slots) {
        return slots.stream().map(slot -> slot.stack().clone()).toList();
    }

    /**
     * @param stack item to check
     * @return whether the item is a shulker box of any colour
     */
    public static boolean isShulkerBox(ItemStack stack) {
        return !isEmpty(stack) && Tag.SHULKER_BOXES.isTagged(stack.getType());
    }

    private static void add(List<DeliveryPart> parts, DeliveryPart taken) {
        for (var index = 0; index < parts.size(); index++) {
            var part = parts.get(index);
            if (part.stack().isSimilar(taken.stack())) {
                parts.set(index, new DeliveryPart(part.line(), part.stack(), part.amount() + taken.amount()));
                return;
            }
        }
        parts.add(taken);
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    private record Candidate(ItemSlot slot, int extras, double damage, boolean wantedLater) {}

    /**
     * A slot holding a copy of an item, for working out a delivery.
     */
    private static final class CopySlot implements ItemSlot {
        private ItemStack stack;

        private CopySlot(ItemStack stack) {
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

    /**
     * A storage slot of a player inventory.
     */
    private record StorageSlot(PlayerInventory inventory, int index) implements ItemSlot {
        @Override
        public ItemStack stack() {
            var stack = inventory.getItem(index);
            return stack == null ? ItemStack.empty() : stack;
        }

        @Override
        public void take(int amount) {
            var stack = stack().clone();
            var left = stack.getAmount() - amount;
            if (left <= 0) {
                inventory.setItem(index, null);
                return;
            }
            stack.setAmount(left);
            inventory.setItem(index, stack);
        }
    }

    /**
     * The content of the shulker box in the main hand. Every change is written back to the box at once.
     */
    private static final class ShulkerContents {
        private final PlayerInventory inventory;
        private final List<ItemStack> items;

        private ShulkerContents(PlayerInventory inventory, List<ItemStack> items) {
            this.inventory = inventory;
            this.items = new ArrayList<>(items);
        }

        static ShulkerContents of(PlayerInventory inventory) {
            var container = inventory.getItemInMainHand().getData(DataComponentTypes.CONTAINER);
            return new ShulkerContents(inventory, container == null ? List.of() : container.contents());
        }

        List<ItemSlot> slots() {
            List<ItemSlot> slots = new ArrayList<>();
            for (var index = 0; index < items.size(); index++) {
                if (isEmpty(items.get(index))) continue;
                slots.add(new ShulkerSlot(this, index));
            }
            return slots;
        }

        ItemStack get(int index) {
            return items.get(index);
        }

        void take(int index, int amount) {
            var stack = items.get(index).clone();
            var left = stack.getAmount() - amount;
            if (left <= 0) {
                items.set(index, ItemStack.empty());
            } else {
                stack.setAmount(left);
                items.set(index, stack);
            }
            var hand = inventory.getItemInMainHand();
            hand.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(items));
            inventory.setItemInMainHand(hand);
        }
    }

    /**
     * A slot of the shulker box in the main hand.
     */
    private record ShulkerSlot(ShulkerContents contents, int index) implements ItemSlot {
        @Override
        public ItemStack stack() {
            return contents.get(index);
        }

        @Override
        public void take(int amount) {
            contents.take(index, amount);
        }
    }
}
