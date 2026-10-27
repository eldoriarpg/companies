/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.criteria.CriteriaText;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The draft of an order: its name and its lines, each with a unit price.
 * <p>
 * Lines are numbered from 1 in the order they were added. A line is either a material line, whose template is the
 * plain item, or an exact line, whose template carries the data of the requested item. Adding an item similar to an
 * existing line of the same kind adds the amounts and takes the newly entered unit price. The total of a line is its
 * unit price times its amount, so changing the amount scales the total. A draft may repeat; finishing it then starts
 * a repeating order instead of a one-off order.
 */
public class OrderBuilder {
    private final SimpleOrder order;
    private final List<DraftLine> lines = new ArrayList<>();
    private Optional<Repetition> repetition = Optional.empty();

    /**
     * @param owner owner of the order
     * @param name  name of the order
     */
    public OrderBuilder(UUID owner, String name) {
        order = new SimpleOrder(owner, name);
    }

    /**
     * Rounds the total of a line to two decimals.
     *
     * @param unitPrice price of one item
     * @param amount    number of items
     * @return the total, rounded half up
     */
    public static double total(double unitPrice, int amount) {
        return BigDecimal.valueOf(unitPrice)
                .multiply(BigDecimal.valueOf(amount))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Adds a line, or adds the amount to a similar line of the same kind and takes the new unit price.
     *
     * @param template  requested item with an amount of one
     * @param exact     whether the data of the item is requested as well
     * @param amount    number of items
     * @param unitPrice price of one item
     * @return number of the line, starting at 1
     */
    public int add(ItemStack template, boolean exact, int amount, double unitPrice) {
        return add(new DraftLine(template, exact, amount, unitPrice));
    }

    /**
     * Adds a criteria line, or adds the amount to a line with equal criteria and takes the new unit price.
     *
     * @param criteria  what the line accepts
     * @param amount    number of items
     * @param unitPrice price of one item
     * @return number of the line, starting at 1
     */
    public int add(ItemCriteria criteria, int amount, double unitPrice) {
        return add(new DraftLine(criteria.template(), false, amount, unitPrice, criteria));
    }

    /**
     * Adds a line, or adds its amount to a similar line and takes its unit price.
     *
     * @param line the line to add
     * @return number of the line, starting at 1
     */
    public int add(DraftLine line) {
        var similar = similarLine(line);
        if (similar.isPresent()) {
            var existing = lines.get(similar.get() - 1);
            lines.set(
                    similar.get() - 1,
                    existing.withAmount(Math.addExact(existing.amount(), line.amount()))
                            .withUnitPrice(line.unitPrice()));
            return similar.get();
        }
        lines.add(line);
        return lines.size();
    }

    /**
     * Finds the line a new item would be merged into.
     *
     * @param template requested item with an amount of one
     * @param exact    whether the data of the item is requested as well
     * @return the number of the similar line, starting at 1
     */
    public Optional<Integer> similarLine(ItemStack template, boolean exact) {
        return similarLine(new DraftLine(template, exact, 1, 0));
    }

    /**
     * Finds the line a new line would be merged into: one of the same kind with a similar template, or for a criteria
     * line one with equal criteria.
     *
     * @param line the new line
     * @return the number of the similar line, starting at 1
     */
    public Optional<Integer> similarLine(DraftLine line) {
        for (var index = 0; index < lines.size(); index++) {
            if (lines.get(index).similar(line)) return Optional.of(index + 1);
        }
        return Optional.empty();
    }

    /**
     * @param line number of the line, starting at 1
     * @return whether the line was removed
     */
    public boolean remove(int line) {
        if (!hasLine(line)) return false;
        lines.remove(line - 1);
        return true;
    }

    /**
     * Changes the amount of a line and keeps its unit price, so the total scales.
     *
     * @param line   number of the line, starting at 1
     * @param amount new amount
     * @return whether the line exists
     */
    public boolean amount(int line, int amount) {
        if (!hasLine(line)) return false;
        var current = lines.get(line - 1);
        lines.set(line - 1, current.withAmount(amount));
        return true;
    }

    /**
     * Changes the unit price of a line.
     *
     * @param line      number of the line, starting at 1
     * @param unitPrice new price of one item
     * @return whether the line exists
     */
    public boolean unitPrice(int line, double unitPrice) {
        if (!hasLine(line)) return false;
        var current = lines.get(line - 1);
        lines.set(line - 1, current.withUnitPrice(unitPrice));
        return true;
    }

    /**
     * @param line number of the line, starting at 1
     * @return the line
     */
    public Optional<DraftLine> line(int line) {
        if (!hasLine(line)) return Optional.empty();
        return Optional.of(lines.get(line - 1));
    }

    /**
     * @return the lines in their order
     */
    public List<DraftLine> lines() {
        return List.copyOf(lines);
    }

    /**
     * @return number of lines
     */
    public int lineCount() {
        return lines.size();
    }

    /**
     * @return number of items over all lines
     */
    public int amount() {
        return lines.stream().mapToInt(DraftLine::amount).sum();
    }

    /**
     * @param line number of the line to leave out, starting at 1
     * @return number of items of all other lines
     */
    public int amountWithout(int line) {
        return amount() - line(line).map(DraftLine::amount).orElse(0);
    }

    /**
     * @return price of the order, the sum of the line totals; infinite when a line total is too large for a number
     */
    public double price() {
        var totals = lines.stream().mapToDouble(DraftLine::total).toArray();
        if (Arrays.stream(totals).anyMatch(total -> !Double.isFinite(total))) return Double.POSITIVE_INFINITY;
        return Arrays.stream(totals)
                .mapToObj(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .doubleValue();
    }

    /**
     * @return name of the order
     */
    public String name() {
        return order.name();
    }

    /**
     * @param name new name of the order
     */
    public void name(String name) {
        order.name(name);
    }

    /**
     * @return how the order repeats once it is finished, empty for a one-off order
     */
    public Optional<Repetition> repetition() {
        return repetition;
    }

    /**
     * @param repetition how the order repeats, empty for a one-off order
     */
    public void repetition(Optional<Repetition> repetition) {
        this.repetition = repetition;
    }

    /**
     * @return owner of the order
     */
    public UUID owner() {
        return order.owner();
    }

    /**
     * Turns the draft into an order which is not saved yet. Each line keeps its position and its rounded total.
     *
     * @param fee fee the owner pays for placing the order
     * @return the order
     */
    public FullOrder build(double fee) {
        List<OrderContent> contents = new ArrayList<>();
        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            contents.add(new OrderContent(
                    index, line.template(), line.exact(), line.criteria(), line.amount(), line.total()));
        }
        return new SimpleOrder(order.owner(), order.name(), fee).toFullOrder(contents);
    }

    /**
     * The market price offered for a line: only material lines have one, and only when the market has data.
     *
     * @param line  line of the draft
     * @param price market price of the line's material
     * @return the price when it may be offered
     */
    public static Optional<MaterialPrice> offeredMarketPrice(DraftLine line, MaterialPrice price) {
        return offeredMarketPrice(line.exact() || line.criteria() != null, price);
    }

    /**
     * The market price offered for an item: exact items have none, material items only when the market has data.
     *
     * @param exact whether the item is requested exactly
     * @param price market price of the item's material
     * @return the price when it may be offered
     */
    public static Optional<MaterialPrice> offeredMarketPrice(boolean exact, MaterialPrice price) {
        if (exact || !price.hasData()) return Optional.empty();
        return Optional.of(price);
    }

    private boolean hasLine(int line) {
        return line >= 1 && line <= lines.size();
    }

    /**
     * One line of a draft.
     *
     * @param template  requested item with an amount of one, for a criteria line the item standing for it
     * @param exact     whether the data of the item is requested as well
     * @param amount    number of items
     * @param unitPrice price of one item
     * @param criteria  what a criteria line accepts, null for material and exact lines
     */
    public record DraftLine(
            ItemStack template,
            boolean exact,
            int amount,
            double unitPrice,
            @Nullable ItemCriteria criteria) {
        /**
         * A material or exact line.
         *
         * @param template  requested item with an amount of one
         * @param exact     whether the data of the item is requested as well
         * @param amount    number of items
         * @param unitPrice price of one item
         */
        public DraftLine(ItemStack template, boolean exact, int amount, double unitPrice) {
            this(template, exact, amount, unitPrice, null);
        }

        /**
         * @param amount new number of items
         * @return a copy with the amount
         */
        public DraftLine withAmount(int amount) {
            return new DraftLine(template, exact, amount, unitPrice, criteria);
        }

        /**
         * @param unitPrice new price of one item
         * @return a copy with the unit price
         */
        public DraftLine withUnitPrice(double unitPrice) {
            return new DraftLine(template, exact, amount, unitPrice, criteria);
        }

        /**
         * Whether another line asks for the same: equal criteria for criteria lines, otherwise the same kind and a
         * similar template.
         *
         * @param other the other line
         * @return true when the lines may be merged
         */
        public boolean similar(DraftLine other) {
            if (criteria != null || other.criteria != null) return Objects.equals(criteria, other.criteria);
            return exact == other.exact && template.isSimilar(other.template);
        }

        /**
         * @return unit price times amount, rounded to two decimals
         */
        public double total() {
            return OrderBuilder.total(unitPrice, amount);
        }

        /**
         * @return the material as the market prices know it
         */
        public String materialIdentifier() {
            return template.getType().name().toLowerCase(Locale.ROOT);
        }

        /**
         * The translated material for material lines, the name of the item with the item as hover for exact lines, the
         * criteria for criteria lines.
         *
         * @return name of the item, to be inserted as placeholder
         */
        public Component itemName() {
            if (criteria != null) return CriteriaText.component(criteria);
            if (!exact) return Component.translatable(template);
            return template.effectiveName().hoverEvent(template.asHoverEvent());
        }
    }
}
