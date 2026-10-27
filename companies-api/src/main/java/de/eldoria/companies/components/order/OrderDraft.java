/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The draft handed to the listeners of {@link de.eldoria.companies.events.order.PreOrderCreateEvent}. Not thread-safe;
 * the event is fired on the main thread.
 */
final class OrderDraft implements IOrderDraft {
    private final List<Line> lines = new ArrayList<>();
    private String name;

    OrderDraft(String name) {
        this.name = checkedName(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void name(String name) {
        this.name = checkedName(name);
    }

    @Override
    public List<? extends IDraftLine> lines() {
        return List.copyOf(lines);
    }

    @Override
    public IDraftLine addLine(Material material, int amount, double unitPrice) {
        Objects.requireNonNull(material, "material");
        return add(ItemStack.of(checkedItem(material)), false, amount, unitPrice);
    }

    @Override
    public IDraftLine addLine(ItemStack template, int amount, double unitPrice) {
        Objects.requireNonNull(template, "template");
        checkedItem(template.getType());
        return add(template.asOne(), true, amount, unitPrice);
    }

    @Override
    public IDraftLine addLine(ItemCriteria criteria, int amount, double unitPrice) {
        Objects.requireNonNull(criteria, "criteria");
        var line = new Line(criteria.template(), false, criteria, checkedAmount(amount), checkedPrice(unitPrice));
        lines.add(line);
        return line;
    }

    @Override
    public int amount() {
        return lines.stream().mapToInt(Line::amount).sum();
    }

    @Override
    public double price() {
        return lines.stream().mapToDouble(Line::price).sum();
    }

    private Line add(ItemStack template, boolean exact, int amount, double unitPrice) {
        var line = new Line(template, exact, null, checkedAmount(amount), checkedPrice(unitPrice));
        lines.add(line);
        return line;
    }

    private static String checkedName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("The name must not be blank");
        return name;
    }

    private static Material checkedItem(Material material) {
        if (material.isAir() || !material.isItem()) {
            throw new IllegalArgumentException("The template must be an item, was " + material);
        }
        return material;
    }

    private static int checkedAmount(int amount) {
        if (amount < 1) throw new IllegalArgumentException("The amount must be at least 1, was " + amount);
        return amount;
    }

    private static double checkedPrice(double unitPrice) {
        if (!Double.isFinite(unitPrice) || unitPrice < 0) {
            throw new IllegalArgumentException("The unit price must be finite and not negative, was " + unitPrice);
        }
        return unitPrice;
    }

    private final class Line implements IDraftLine {
        private final ItemStack template;
        private final boolean exact;
        private final @Nullable ItemCriteria criteria;
        private int amount;
        private double unitPrice;
        private boolean removed;

        private Line(ItemStack template, boolean exact, @Nullable ItemCriteria criteria, int amount, double unitPrice) {
            this.template = template;
            this.exact = exact;
            this.criteria = criteria;
            this.amount = amount;
            this.unitPrice = unitPrice;
        }

        @Override
        public ItemStack template() {
            return template.clone();
        }

        @Override
        public boolean exact() {
            return exact;
        }

        @Override
        public Optional<ItemCriteria> criteria() {
            return Optional.ofNullable(criteria);
        }

        @Override
        public int amount() {
            return amount;
        }

        @Override
        public void amount(int amount) {
            checkPresent();
            this.amount = checkedAmount(amount);
        }

        @Override
        public double unitPrice() {
            return unitPrice;
        }

        @Override
        public void unitPrice(double unitPrice) {
            checkPresent();
            this.unitPrice = checkedPrice(unitPrice);
        }

        @Override
        public double price() {
            return unitPrice * amount;
        }

        @Override
        public void remove() {
            removed = true;
            lines.remove(this);
        }

        @Override
        public boolean removed() {
            return removed;
        }

        private void checkPresent() {
            if (removed) throw new IllegalStateException("The line was removed from its draft");
        }
    }
}
