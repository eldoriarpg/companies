/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.orders.ItemMatcher;
import de.eldoria.companies.orders.criteria.CriteriaText;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One line of an order as the order views show it. It holds no worker and no counterpart, so a view built from it
 * can not reveal who works on or who placed the order.
 *
 * @param number      number of the line, from one as in commands
 * @param template    requested item with an amount of one
 * @param exact       whether the data of the item is requested as well
 * @param amount      requested amount
 * @param delivered   delivered amount
 * @param price       total price of the line
 * @param damaged     how many delivered items are damaged
 * @param worstDamage wear of the most worn delivered item, from 0 to 1
 * @param criteria    what a criteria line accepts, null for material and exact lines
 */
public record OrderLineView(
        int number,
        ItemStack template,
        boolean exact,
        int amount,
        int delivered,
        double price,
        int damaged,
        double worstDamage,
        @Nullable ItemCriteria criteria) {
    /**
     * The lines of an order without their damage.
     *
     * @param order order to show
     * @return its lines
     */
    public static List<OrderLineView> of(FullOrder order) {
        return order.contents().stream().map(OrderLineView::withoutDamage).toList();
    }

    /**
     * The lines of an order with the damage of their delivered items, on the lines that accept damaged items: every
     * line when the server allows them, otherwise the criteria lines with a durability condition.
     *
     * @param order   order to show
     * @param matcher measures the damage
     * @return its lines
     */
    public static List<OrderLineView> withDamage(FullOrder order, ItemMatcher matcher) {
        return order.contents().stream()
                .map(content -> takesDamaged(content, matcher) ? withDamage(content, matcher) : withoutDamage(content))
                .toList();
    }

    private static boolean takesDamaged(OrderContent content, ItemMatcher matcher) {
        return matcher.allowDamaged()
                || content.criteria()
                        .map(criteria -> criteria.minDurability() > 0)
                        .orElse(false);
    }

    private static OrderLineView withoutDamage(OrderContent content) {
        return new OrderLineView(
                content.line() + 1,
                content.stack(),
                content.exact(),
                content.amount(),
                content.delivered(),
                content.price(),
                0,
                0,
                content.criteria().orElse(null));
    }

    private static OrderLineView withDamage(OrderContent content, ItemMatcher matcher) {
        var damaged = 0;
        var worst = 0.0;
        for (var delivery : content.deliveries()) {
            var damage = matcher.damage(delivery.stack());
            if (damage <= 0) continue;
            damaged += delivery.amount();
            worst = Math.max(worst, damage);
        }
        return new OrderLineView(
                content.line() + 1,
                content.stack(),
                content.exact(),
                content.amount(),
                content.delivered(),
                content.price(),
                damaged,
                worst,
                content.criteria().orElse(null));
    }

    /**
     * The name of the requested item: the translated material, or for an exact line the item's own name with the
     * item shown when hovered.
     *
     * @return name of the item, to be inserted as a placeholder
     */
    public Component itemName() {
        if (criteria != null) return CriteriaText.component(criteria);
        if (!exact) return Component.translatable(template);
        return template.effectiveName().hoverEvent(template.asHoverEvent());
    }

    /**
     * @return how many items the line still misses
     */
    public int missing() {
        return Math.max(0, amount - delivered);
    }

    /**
     * @return delivered share from 0 to 1
     */
    public double progress() {
        if (amount <= 0) return 0;
        return delivered / (double) amount;
    }

    /**
     * @return wear of the most worn delivered item in whole percent
     */
    public long worstDamagePercent() {
        return Math.round(worstDamage * 100);
    }
}
