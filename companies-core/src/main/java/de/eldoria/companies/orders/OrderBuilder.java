/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        var similar = similarLine(template, exact);
        if (similar.isPresent()) {
            var existing = lines.get(similar.get() - 1);
            lines.set(
                    similar.get() - 1,
                    new DraftLine(existing.template(), exact, Math.addExact(existing.amount(), amount), unitPrice));
            return similar.get();
        }
        lines.add(new DraftLine(template, exact, amount, unitPrice));
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
        for (var index = 0; index < lines.size(); index++) {
            var existing = lines.get(index);
            if (existing.exact() == exact && existing.template().isSimilar(template)) return Optional.of(index + 1);
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
        lines.set(line - 1, new DraftLine(current.template(), current.exact(), amount, current.unitPrice()));
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
        lines.set(line - 1, new DraftLine(current.template(), current.exact(), current.amount(), unitPrice));
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
     * @return price of the order, the sum of the line totals
     */
    public double price() {
        return lines.stream()
                .map(line -> BigDecimal.valueOf(line.total()))
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
            contents.add(new OrderContent(index, line.template(), line.exact(), line.amount(), line.total()));
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
        return offeredMarketPrice(line.exact(), price);
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
     * @param template  requested item with an amount of one
     * @param exact     whether the data of the item is requested as well
     * @param amount    number of items
     * @param unitPrice price of one item
     */
    public record DraftLine(ItemStack template, boolean exact, int amount, double unitPrice) {
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
         * The translated material for material lines, the name of the item with the item as hover for exact lines.
         *
         * @return name of the item, to be inserted as placeholder
         */
        public Component itemName() {
            if (!exact) return Component.translatable(template);
            return template.effectiveName().hoverEvent(template.asHoverEvent());
        }
    }
}
