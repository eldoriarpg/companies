/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
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
 * unit price times its amount, so changing the amount scales the total.
 */
public class OrderBuilder {
    private final SimpleOrder order;
    private final List<DraftLine> lines = new ArrayList<>();

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
                    new DraftLine(existing.template(), exact, existing.amount() + amount, unitPrice));
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
     * Renders the builder menu: the name, every line with its unit price, amount and total and buttons to change it,
     * the limits, the price with the fee, and the done and cancel buttons.
     * <p>
     * Item names are inserted as placeholders, since the name of an exact item may contain tags. The market average
     * button and the price hover appear only on material lines with market data.
     *
     * @param setting   order settings providing the limits and the fee
     * @param economy   economy formatting the amounts
     * @param orderData order data providing the market prices
     * @return composer holding the menu and its placeholders
     */
    public MessageComposer asComponent(OrderSettings setting, Economy economy, AOrderData orderData) {
        var cmd = "/order draft";
        var composer = MessageComposer.create()
                .text("<name>%s <click:suggest_command:%s name ><modify>[", Texts.escape(name()), cmd)
                .localeCode("words.change")
                .text("]</click>")
                .newLine()
                .text("<name>")
                .localeCode("words.items")
                .text(": ");

        if (setting.maxItems() > amount() && lineCount() < setting.maxMaterials()) {
            composer.space()
                    .text("<click:suggest_command:%s add ><add>[", cmd)
                    .localeCode("words.add")
                    .text("]</click> <click:suggest_command:%s add hand ><add>[", cmd)
                    .localeCode("order.draft.addHand")
                    .text("]</click>");
        }

        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            var number = index + 1;
            var itemTag = "draft_item_" + number;
            var price = marketPrice(line, orderData);
            composer.newLine().space(2);
            if (price.isPresent()) {
                composer.text(
                        "<value><hover:show_text:'%s'><%s></hover>", price.get().asComponent(1, economy), itemTag);
            } else {
                composer.text("<value><%s>", itemTag);
            }
            composer.text(
                            " <value>%s × %s = %s ",
                            economy.format(line.unitPrice()), line.amount(), economy.format(line.total()))
                    .text("<click:run_command:%s remove %s><remove>[", cmd, number)
                    .localeCode("words.remove", Placeholder.component(itemTag, line.itemName()))
                    .text("]</click> <click:suggest_command:%s amount %s ><modify>[", cmd, number)
                    .localeCode("words.amount")
                    .text("]</click> <click:suggest_command:%s price %s ><modify>[", cmd, number)
                    .localeCode("words.price")
                    .text("]</click>");
            if (price.isPresent()) {
                composer.text(" <click:run_command:%s price %s market><modify>[", cmd, number)
                        .localeCode("order.draft.marketAverage")
                        .text("]</click>");
            }
        }
        composer.newLine()
                .text("<name>")
                .localeCode("words.lines")
                .text(": <value>%s/%s", lineCount(), setting.maxMaterials())
                .newLine()
                .text("<name>")
                .localeCode("words.items")
                .text(": <value>%s/%s", amount(), setting.maxItems())
                .newLine()
                .text("<name>")
                .localeCode("words.price")
                .text(": <value>%s", economy.format(price()));
        PriceLines.fee(composer, economy, price(), setting.fee().of(price()))
                .newLine()
                .text("<click:run_command:%s done><add>[", cmd)
                .localeCode("words.done")
                .text("]</click>")
                .space()
                .text("<click:run_command:%s cancel><remove>[", cmd)
                .localeCode("words.cancel")
                .text("]</click>");
        return composer;
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

    private static Optional<MaterialPrice> marketPrice(DraftLine line, AOrderData orderData) {
        if (line.exact()) return Optional.empty();
        return offeredMarketPrice(line, orderData.getMaterialPrice(line.materialIdentifier()));
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
