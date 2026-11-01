/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.configuration.elements.systemorders.RuleEntry;
import de.eldoria.companies.configuration.elements.systemorders.SystemOrderRule;
import de.eldoria.companies.orders.criteria.CriteriaRules;
import de.eldoria.companies.orders.criteria.CriteriaSyntax;
import de.eldoria.companies.orders.criteria.CriteriaText;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Turns an entry of a defined rule into what one order asks for.
 * <ul>
 *     <li>A material entry orders that material; its market average can price it.</li>
 *     <li>A criteria entry with {@code resolve: ANY} orders a criteria line accepting the whole set.</li>
 *     <li>A criteria entry with {@code resolve: ONE} picks one material of the set at random, leaving out materials the
 *     server does not know, that rest, or that can not meet the conditions. Without conditions it orders
 *     that material, which its market average can price; with them a criteria line of that one material.</li>
 *     <li>An item entry orders exactly the captured item.</li>
 * </ul>
 * Only orders of a single plain material have a market average, so the others need a fixed price. An entry that can
 * not be ordered is passed over for the next one, so a rule never stalls on one entry.
 */
public final class RuleItems {
    private RuleItems() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Picks the entry the next order of a rule takes.
     *
     * @param rule   the rule
     * @param count  how many entries the rule has, at least 1
     * @param last   the entry the rule's last order took, null when unknown
     * @param random a random number from 0 included to 1 excluded
     * @return the index of the entry
     */
    public static int pick(SystemOrderRule rule, int count, @Nullable Integer last, DoubleSupplier random) {
        if (count <= 1) return 0;
        if (rule.pick() == SystemOrderRule.Pick.ROTATE) return last == null ? 0 : Math.floorMod(last + 1, count);
        return Math.min(count - 1, (int) Math.floor(random.getAsDouble() * count));
    }

    /**
     * Resolves an entry.
     *
     * @param entry   the entry
     * @param index   its place in the rule
     * @param knows   whether a material name names an item that can be ordered
     * @param resting the materials that rest, read only when a {@code ONE} entry needs them
     * @param random  a random number from 0 included to 1 excluded
     * @return what the order asks for, or why the entry can not be ordered
     */
    public static Resolved resolve(
            RuleEntry entry, int index, Predicate<String> knows, Supplier<Set<String>> resting, DoubleSupplier random) {
        var item = entry.item();
        if (item != null) {
            var name = entry.name().isBlank() ? item.getType().name() : entry.name();
            return new Chosen(index, name, new Line(item, true, null), null);
        }
        if (!entry.criteria().isEmpty()) return criteria(entry, index, knows, resting, random);
        var material = entry.material();
        if (material.isEmpty() || !knows.test(material)) return new Problem(material);
        return new Chosen(index, material, null, material);
    }

    private static Resolved criteria(
            RuleEntry entry, int index, Predicate<String> knows, Supplier<Set<String>> resting, DoubleSupplier random) {
        ItemCriteria criteria;
        try {
            criteria = CriteriaSyntax.parse(entry.criteria());
        } catch (CommandFailure failure) {
            return new Problem(entry.criteria());
        }
        if (CriteriaRules.problem(criteria).isPresent()) return new Problem(entry.criteria());
        if (entry.resolve() == RuleEntry.Resolve.ANY) {
            return new Chosen(
                    index, CriteriaText.plain(criteria), new Line(criteria.template(), false, criteria), null);
        }
        List<ItemCriteria> candidates = criteria.materials().stream()
                .map(material -> material.name())
                .filter(knows)
                .filter(material -> !resting.get().contains(material))
                .map(material -> new ItemCriteria(
                        List.of(material),
                        criteria.minEnchantments(),
                        criteria.enchantments(),
                        criteria.potion(),
                        criteria.minDurability()))
                .filter(single -> CriteriaRules.problem(single).isEmpty())
                .toList();
        if (candidates.isEmpty()) return new Problem(entry.criteria());
        var single = candidates.get(
                Math.min(candidates.size() - 1, (int) Math.floor(random.getAsDouble() * candidates.size())));
        var material = single.items().getFirst();
        if (!single.hasConditions()) {
            return new Chosen(index, material, null, material);
        }
        return new Chosen(index, CriteriaText.plain(single), new Line(single.template(), false, single), null);
    }

    /**
     * What came of resolving an entry.
     */
    public sealed interface Resolved permits Chosen, Problem {}

    /**
     * What an order of the rule asks for.
     *
     * @param entry    the index of the entry
     * @param label    what is ordered, as decisions and admins see it: a material, the criteria or the item's name
     * @param line     the line to order, null for a plain material named by {@code material}
     * @param material the material of a plain material order, which also has a market average; null otherwise
     */
    public record Chosen(
            int entry,
            String label,
            @Nullable Line line,
            @Nullable String material) implements Resolved {}

    /**
     * The entry names nothing that can be ordered.
     *
     * @param entry the entry as written
     */
    public record Problem(String entry) implements Resolved {}

    /**
     * A line other than a plain material.
     *
     * @param template the item, for criteria the item standing for them
     * @param exact    whether the item's data is requested
     * @param criteria the criteria of a criteria line, null otherwise
     */
    public record Line(
            ItemStack template, boolean exact, @Nullable ItemCriteria criteria) {}
}
