/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.components.order.IOrderContent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One line of an order with the stacks delivered to it.
 * <p>
 * Workers are paid per delivered amount of a line, so {@link #parts()} sums the delivered stacks per worker.
 */
public class OrderContent implements IOrderContent {
    private final int line;
    private final ItemStack stack;
    private final boolean exact;
    private int amount;
    private List<DeliveredStack> deliveries = new ArrayList<>();
    private double price;

    /**
     * Creates a line.
     *
     * @param line   position of the line in its order
     * @param stack  requested item with an amount of one
     * @param exact  whether the data of the item is requested as well
     * @param amount requested amount
     * @param price  total price of the line
     */
    public OrderContent(int line, ItemStack stack, boolean exact, int amount, double price) {
        this.line = line;
        this.stack = stack;
        this.exact = exact;
        this.amount = amount;
        this.price = price;
    }

    /**
     * Sets the stacks delivered to this line.
     *
     * @param deliveries delivered stacks
     */
    public void deliveries(List<DeliveredStack> deliveries) {
        this.deliveries = deliveries;
    }

    @Override
    public List<DeliveredStack> deliveries() {
        return deliveries;
    }

    @Override
    public int line() {
        return line;
    }

    @Override
    public boolean exact() {
        return exact;
    }

    @Override
    public double unitPrice() {
        if (amount == 0) return 0;
        return price / amount;
    }

    @Override
    public int amount() {
        return amount;
    }

    @Override
    public void amount(int amount) {
        this.amount = amount;
    }

    /**
     * The delivered amount per worker, in the order the workers first delivered.
     *
     * @return one part per worker
     */
    @Override
    public List<ContentPart> parts() {
        Map<UUID, Integer> amounts = new LinkedHashMap<>();
        for (var delivery : deliveries) {
            amounts.merge(delivery.worker(), delivery.amount(), Integer::sum);
        }
        return amounts.entrySet().stream()
                .map(entry -> new ContentPart(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public double price() {
        return price;
    }

    @Override
    public ItemStack stack() {
        return stack;
    }

    @Override
    public int delivered() {
        return deliveries.stream().mapToInt(DeliveredStack::amount).sum();
    }

    @Override
    public double percent() {
        return delivered() / (double) amount;
    }

    @Override
    public String materialIdentifier() {
        return stack.getType().name().toLowerCase();
    }

    @Override
    public String translatedMaterialString() {
        return "<tr:%s>".formatted(stack.translationKey());
    }

    /**
     * The name of the requested item.
     * <p>
     * A material line shows the translated material. An exact line shows the name of its template with the item as
     * hover. The component has to be inserted as a placeholder, never as MiniMessage text, since a custom name may
     * contain tags.
     *
     * @return name of the item
     */
    public Component itemName() {
        if (!exact) return Component.translatable(stack);
        return stack.effectiveName().hoverEvent(stack.asHoverEvent());
    }

    @Override
    public Material material() {
        return stack.getType();
    }

    @Override
    public int missing() {
        return amount - delivered();
    }

    public void price(double price) {
        this.price = price;
    }

    public Map<UUID, Double> payments() {
        Map<UUID, Double> payments = new HashMap<>();
        for (var part : parts()) {
            payments.merge(part.worker(), (part.amount() / (double) amount) * price, Double::sum);
        }
        return payments;
    }

    public Map<UUID, Integer> workerAmount() {
        Map<UUID, Integer> workerAmount = new HashMap<>();
        for (var part : parts()) {
            workerAmount.merge(part.worker(), part.amount(), Integer::sum);
        }
        return workerAmount;
    }
}
