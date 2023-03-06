/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.components.order.IOrderContent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class
OrderContent implements IOrderContent {
    private ItemStack stack;
    private int amount;
    private List<ContentPart> parts = new ArrayList<>();
    private double price;

    public OrderContent(ItemStack stack, int amount, double price) {
        this.stack = stack;
        this.amount = amount;
        this.price = price;
    }

    public void parts(List<ContentPart> parts) {
        this.parts = parts;
    }

    @Override
    public ItemStack stack() {
        return stack;
    }

    @Override
    public int amount() {
        return amount;
    }

    @Override
    public void amount(int amount) {
        this.amount = amount;
    }

    @Override
    public List<ContentPart> parts() {
        return parts;
    }

    @Override
    public double price() {
        return price;
    }

    public void price(double price) {
        this.price = price;
    }

    public String asComponent(Economy economy) {
        return String.format("<%s>%s <%s>%sx <%s>%s", "yellow", prettyType(), "blue", amount, "gold", economy.format(price));
    }

    public String asProgressComponent(Economy economy) {
        return String.format("<%s>%s <%s>%s/%s <%s>%s", "yellow", prettyType(), "blue", delivered(), amount, "gold", economy.format(price));
    }

    @Override
    public int delivered() {
        return parts.stream().mapToInt(ContentPart::amount).sum();
    }

    @Override
    public double percent() {
        return delivered() / (double) amount;
    }

    @Override
    public String materialString() {
        return stack.getType().name().toLowerCase();
    }

    @Override
    public Material material() {
        return stack.getType();
    }

    @Override
    public int missing() {
        return amount - delivered();
    }

    public Map<UUID, Double> payments() {
        Map<UUID, Double> payments = new HashMap<>();
        for (var part : parts) {
            payments.put(part.worker(), (part.amount() / (double) amount) * price);
        }
        return payments;
    }

    public Map<UUID, Integer> workerAmount() {
        Map<UUID, Integer> workerAmount = new HashMap<>();
        for (var part : parts) {
            workerAmount.put(part.worker(), part.amount());
        }
        return workerAmount;
    }
}
