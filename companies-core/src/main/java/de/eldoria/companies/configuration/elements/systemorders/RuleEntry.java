/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.systemorders;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One thing a rule may order, one entry of its {@code items}. Exactly one of three is set:
 * <ul>
 *     <li>{@code material}: any item of a material, such as {@code OAK_LOG}.</li>
 *     <li>{@code criteria}: criteria in the text form players type, such as {@code #logs bamboo_block enchants:2}.
 *     {@code resolve} decides whether the order accepts any item of the set ({@code ANY}) or asks for one material
 *     picked from it ({@code ONE}).</li>
 *     <li>{@code item}: an exact item, captured in game with {@code /companyadmin systemorders item add <rule>}.
 *     {@code name} keeps its name for the rules editor.</li>
 * </ul>
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RuleEntry {
    private String material = "";
    private String criteria = "";
    private Resolve resolve = Resolve.ANY;
    private @Nullable ItemStack item = null;
    private String name = "";

    public RuleEntry() {}

    /**
     * @param material the material
     * @return an entry ordering any item of the material
     */
    public static RuleEntry material(String material) {
        var entry = new RuleEntry();
        entry.material = material;
        return entry;
    }

    /**
     * @param criteria criteria in the text form
     * @param resolve  what the order accepts of the set
     * @return an entry ordering items meeting the criteria
     */
    public static RuleEntry criteria(String criteria, Resolve resolve) {
        var entry = new RuleEntry();
        entry.criteria = criteria;
        entry.resolve = resolve;
        return entry;
    }

    /**
     * @param item the exact item
     * @param name its name, shown in the rules editor
     * @return an entry ordering exactly that item
     */
    public static RuleEntry item(ItemStack item, String name) {
        var entry = new RuleEntry();
        entry.item = item.asOne();
        entry.name = name;
        return entry;
    }

    /**
     * @return the material as written, blank unless this is a material entry
     */
    public String material() {
        return material == null ? "" : material.trim();
    }

    /**
     * @return the criteria as written, blank unless this is a criteria entry
     */
    public String criteria() {
        return criteria == null ? "" : criteria.trim();
    }

    /**
     * @return what an order of a criteria entry accepts
     */
    public Resolve resolve() {
        return resolve == null ? Resolve.ANY : resolve;
    }

    /**
     * @return a copy of the exact item, null unless this is an item entry
     */
    public @Nullable ItemStack item() {
        return item == null ? null : item.clone();
    }

    /**
     * @return the name of an exact item as it was captured
     */
    public String name() {
        return name == null ? "" : name;
    }

    /**
     * What an order of a criteria entry accepts.
     */
    public enum Resolve {
        /**
         * Any item of the set, mixed.
         */
        ANY,
        /**
         * One material picked from the set for each order.
         */
        ONE
    }
}
