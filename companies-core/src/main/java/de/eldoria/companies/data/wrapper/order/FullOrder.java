/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.orders.PaymentType;
import de.eldoria.companies.util.Inventories;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FullOrder extends SimpleOrder implements de.eldoria.companies.components.order.IFullOrder {
    private final List<OrderContent> contents;

    public FullOrder(
            int id,
            UUID owner,
            String name,
            LocalDateTime created,
            int company,
            LocalDateTime claimed,
            OrderState state,
            double fee,
            int extensions,
            int deliveryBonus,
            List<OrderContent> contents) {
        this(id, owner, name, created, company, claimed, state, fee, extensions, deliveryBonus, 0, contents);
    }

    /**
     * Creates a saved order with its lines that may be a copy of a repeating order.
     *
     * @param id            id of the order
     * @param owner         owner of the order
     * @param name          name of the order
     * @param created       when the order was placed
     * @param company       company working on the order, or 0 when none
     * @param claimed       last state change
     * @param state         state of the order
     * @param fee           fee the owner paid
     * @param extensions    how often the owner extended the purge timer
     * @param deliveryBonus extra delivery hours of the accepting company
     * @param seriesId      id of the repeating order that posted this copy, or 0 for a one-off order
     * @param contents      lines of the order
     */
    public FullOrder(
            int id,
            UUID owner,
            String name,
            LocalDateTime created,
            int company,
            LocalDateTime claimed,
            OrderState state,
            double fee,
            int extensions,
            int deliveryBonus,
            int seriesId,
            List<OrderContent> contents) {
        this(
                id,
                owner,
                name,
                created,
                company,
                claimed,
                state,
                fee,
                extensions,
                deliveryBonus,
                seriesId,
                null,
                contents);
    }

    /**
     * Creates a saved order with its lines that a company or a single player may have claimed.
     *
     * @param id            id of the order
     * @param owner         owner of the order
     * @param name          name of the order
     * @param created       when the order was placed
     * @param company       company working on the order, or 0 when none
     * @param claimed       last state change
     * @param state         state of the order
     * @param fee           fee the owner paid
     * @param extensions    how often the owner extended the purge timer
     * @param deliveryBonus extra delivery hours of the claimant
     * @param seriesId      id of the repeating order that posted this copy, or 0 for a one-off order
     * @param soloWorker    player working on the order alone, or null when a company or nobody claimed it
     * @param contents      lines of the order
     */
    public FullOrder(
            int id,
            UUID owner,
            String name,
            LocalDateTime created,
            int company,
            LocalDateTime claimed,
            OrderState state,
            double fee,
            int extensions,
            int deliveryBonus,
            int seriesId,
            @Nullable UUID soloWorker,
            List<OrderContent> contents) {
        super(id, owner, name, created, company, claimed, state, fee, extensions, deliveryBonus, seriesId, soloWorker);
        this.contents = contents;
    }

    @Override
    public List<OrderContent> contents() {
        return contents;
    }

    @Override
    public double price() {
        return contents.stream().mapToDouble(OrderContent::price).sum();
    }

    @Override
    public int amount() {
        return contents.stream().mapToInt(OrderContent::amount).sum();
    }

    @Override
    public int materialsAmount() {
        return contents.size();
    }

    @Override
    public int delivered() {
        return contents.stream().mapToInt(OrderContent::delivered).sum();
    }

    /**
     * The delivered share of the ordered items.
     *
     * @return a value from 0 to 1, or 0 when nothing is ordered
     */
    @Override
    public double progress() {
        var amount = amount();
        if (amount == 0) return 0;
        return delivered() / (double) amount;
    }

    @Override
    public Optional<OrderContent> line(int line) {
        return contents.stream().filter(content -> content.line() == line).findFirst();
    }

    @Override
    @Deprecated
    public Optional<OrderContent> content(Material material) {
        return contents.stream()
                .filter(content -> !content.exact() && content.criteria().isEmpty() && content.material() == material)
                .findFirst();
    }

    @Override
    public boolean isDone() {
        return delivered() >= amount();
    }

    /**
     * The delivered items of every line, as they were delivered, split into stacks of at most their max stack size.
     *
     * @return delivered stacks
     */
    public List<ItemStack> deliveredStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (var content : contents) {
            for (var delivery : content.deliveries()) {
                stacks.addAll(Inventories.split(delivery.stack(), delivery.amount()));
            }
        }
        return stacks;
    }

    public Map<UUID, Double> payments(PaymentType paymentType) {
        Map<UUID, Double> payments = new HashMap<>();
        switch (paymentType) {
            case STACK -> {
                for (var content : contents) {
                    for (var entry : content.payments().entrySet()) {
                        payments.compute(
                                entry.getKey(), (id, v) -> v == null ? entry.getValue() : v + entry.getValue());
                    }
                }
            }
            case TOTAL -> {
                Map<UUID, Integer> workers = new HashMap<>();
                for (var content : contents) {
                    for (var entry : content.workerAmount().entrySet()) {
                        workers.compute(entry.getKey(), (id, v) -> v == null ? entry.getValue() : v + entry.getValue());
                    }
                }
                var amount = amount();
                var price = price();
                for (var entry : workers.entrySet()) {
                    payments.put(entry.getKey(), (entry.getValue() / (double) amount) * price);
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + paymentType);
        }
        return payments;
    }
}
