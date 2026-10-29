/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

import de.eldoria.companies.configuration.elements.systemorders.RuleEntry;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What an offer orders, written as an entry of a system order rule: a material, criteria in the text form players type,
 * or an exact item. Exactly one of the three is set.
 *
 * @param material the material of a material line, null otherwise
 * @param criteria the criteria of a criteria line, null otherwise
 * @param resolve  what a criteria line accepts of its set; null for the other lines
 * @param item     the exact item of an item line, null otherwise
 * @param label    what is ordered, as players and admins see it
 */
public record OfferLine(
        @Nullable String material,
        @Nullable String criteria,
        RuleEntry.@Nullable Resolve resolve,
        @Nullable ItemStack item,
        String label) {
    /**
     * @param material the material
     * @return a line ordering any item of the material
     */
    public static OfferLine material(String material) {
        return new OfferLine(material, null, null, null, material);
    }

    /**
     * @param criteria criteria in the text form
     * @param resolve  what an order accepts of the set
     * @return a line ordering items meeting the criteria
     */
    public static OfferLine criteria(String criteria, RuleEntry.Resolve resolve) {
        return new OfferLine(null, criteria, resolve, null, criteria);
    }

    /**
     * @param item the exact item
     * @param name its name
     * @return a line ordering exactly that item
     */
    public static OfferLine item(ItemStack item, String name) {
        return new OfferLine(null, null, null, item.asOne(), name);
    }

    /**
     * @return the line as an entry of a system order rule, which resolves it into what one chunk orders
     */
    public RuleEntry entry() {
        if (item != null) return RuleEntry.item(item, label);
        if (criteria != null) return RuleEntry.criteria(criteria, resolve == null ? RuleEntry.Resolve.ANY : resolve);
        return RuleEntry.material(material == null ? "" : material);
    }
}
