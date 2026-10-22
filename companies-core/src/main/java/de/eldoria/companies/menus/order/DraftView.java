/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.orders.OrderBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * What the order builder shows of a draft: its name, its lines, the limits and the money.
 *
 * @param name     name of the order
 * @param lines    lines in their order, numbered from 1
 * @param maxLines most lines an order may have
 * @param items    number of items over all lines
 * @param maxItems most items an order may have
 * @param price    price of the order, the sum of the line totals
 * @param fee      fee the owner pays on top of the price
 */
public record DraftView(
        String name, List<Line> lines, int maxLines, int items, int maxItems, double price, double fee) {
    /**
     * @param name     name of the order
     * @param lines    lines in their order, numbered from 1
     * @param maxLines most lines an order may have
     * @param items    number of items over all lines
     * @param maxItems most items an order may have
     * @param price    price of the order, the sum of the line totals
     * @param fee      fee the owner pays on top of the price
     */
    public DraftView {
        lines = List.copyOf(lines);
    }

    /**
     * Builds the view of a draft.
     *
     * @param draft    the draft
     * @param settings order settings providing the limits and the fee
     * @param prices   market price of a material, by its identifier
     * @return the view
     */
    public static DraftView of(OrderBuilder draft, OrderSettings settings, Function<String, MaterialPrice> prices) {
        var lines = new ArrayList<Line>();
        var number = 1;
        for (var line : draft.lines()) {
            lines.add(Line.of(number++, line, prices));
        }
        var price = draft.price();
        return new DraftView(
                draft.name(),
                lines,
                settings.maxMaterials(),
                draft.amount(),
                settings.maxItems(),
                price,
                settings.fee().of(price));
    }

    /**
     * Whether the add buttons are offered: the draft is below both the item and the line limit.
     *
     * @return true when items may be added
     */
    public boolean canAdd() {
        return items < maxItems && lines.size() < maxLines;
    }

    /**
     * @return how many items may still be added
     */
    public int remainingItems() {
        return Math.max(0, maxItems - items);
    }

    /**
     * The most items a line may have, given the items of the other lines.
     *
     * @param line the line
     * @return the largest amount the line may be set to, at least 1
     */
    public int maxAmount(Line line) {
        return Math.max(1, maxItems - (items - line.amount()));
    }

    /**
     * @param number number of the line, starting at 1
     * @return the line
     */
    public Optional<Line> line(int number) {
        if (number < 1 || number > lines.size()) return Optional.empty();
        return Optional.of(lines.get(number - 1));
    }

    /**
     * @return price and fee
     */
    public double total() {
        return price + fee;
    }

    /**
     * One line of the draft.
     *
     * @param number number of the line, starting at 1
     * @param line   the line
     * @param market market price offered for the line; only material lines with market data have one
     */
    public record Line(int number, OrderBuilder.DraftLine line, Optional<MaterialPrice> market) {
        /**
         * @param number number of the line, starting at 1
         * @param line   line of the draft
         * @param prices market price of a material, by its identifier
         * @return the view of the line
         */
        static Line of(int number, OrderBuilder.DraftLine line, Function<String, MaterialPrice> prices) {
            if (line.exact()) return new Line(number, line, Optional.empty());
            return new Line(
                    number, line, OrderBuilder.offeredMarketPrice(line, prices.apply(line.materialIdentifier())));
        }

        /**
         * @return requested item with an amount of one
         */
        public ItemStack template() {
            return line.template();
        }

        /**
         * @return number of items
         */
        public int amount() {
            return line.amount();
        }

        /**
         * @return price of one item
         */
        public double unitPrice() {
            return line.unitPrice();
        }

        /**
         * @return unit price times amount, rounded to two decimals
         */
        public double total() {
            return line.total();
        }

        /**
         * @return name of the item, see {@link OrderBuilder.DraftLine#itemName()}
         */
        public Component itemName() {
            return line.itemName();
        }
    }
}
