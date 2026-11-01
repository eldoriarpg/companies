/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.criteria;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.eldoutilities.messages.Replacement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The text form players type criteria in, in commands and dialogs: {@code #logs bamboo_block enchants:2 sharpness:3}.
 * <p>
 * Entries are separated by spaces or commas. An entry starting with {@code #} is an item tag, {@code enchants:<n>} is
 * the fewest enchantments an item must carry, {@code <enchantment>:<level>} requires an enchantment at a minimum level,
 * {@code potion:<type>} takes only items of a potion type, {@code durability:<percent>} only items with at least that
 * share of durability left, and every other entry is a material. A count, level or share that is no number, out of
 * range or given twice, and a second potion, are refused. Keys may carry a namespace, such as
 * {@code minecraft:sharpness:3} or {@code potion:minecraft:swiftness}.
 */
public final class CriteriaSyntax {
    /**
     * The entry that sets the fewest enchantments.
     */
    public static final String COUNT = "enchants";

    /**
     * The entry that sets the potion type.
     */
    public static final String POTION = "potion";

    /**
     * The entry that sets the least share of durability left.
     */
    public static final String DURABILITY = "durability";

    private static final Pattern SEPARATOR = Pattern.compile("[\\s,]+");
    private static final Pattern LEVELLED = Pattern.compile("(.+):(-?\\d+)");
    private static final Pattern COUNTED =
            Pattern.compile("(" + COUNT + "|" + DURABILITY + "):.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern POTIONED = Pattern.compile(POTION + ":(.*)", Pattern.CASE_INSENSITIVE);

    private CriteriaSyntax() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Whether a text asks for criteria rather than one material: it names a tag, more than one entry or a condition.
     *
     * @param text entered text
     * @return true when the text is meant as criteria
     */
    public static boolean isCriteria(String text) {
        var entries = entries(text);
        if (entries.size() > 1) return true;
        if (entries.size() != 1) return false;
        var entry = entries.getFirst();
        return ItemCriteria.isTag(entry)
                || LEVELLED.matcher(entry).matches()
                || COUNTED.matcher(entry).matches()
                || POTIONED.matcher(entry).matches();
    }

    /**
     * Reads criteria. Whether the items, tags and enchantments exist is checked by {@link CriteriaRules}.
     *
     * @param text entered text
     * @return the criteria
     * @throws CommandFailure when the text names no item or an entry can not be read
     */
    public static ItemCriteria parse(String text) {
        List<String> items = new ArrayList<>();
        List<ItemCriteria.Required> enchantments = new ArrayList<>();
        Integer count = null;
        Integer durability = null;
        String potion = null;
        for (var entry : entries(text)) {
            var potioned = POTIONED.matcher(entry);
            if (potioned.matches()) {
                if (potion != null || potioned.group(1).isBlank()) throw failure(entry);
                potion = potioned.group(1);
                continue;
            }
            var levelled = LEVELLED.matcher(entry);
            if (!levelled.matches() && COUNTED.matcher(entry).matches()) throw failure(entry);
            if (ItemCriteria.isTag(entry) || !levelled.matches()) {
                items.add(entry);
                continue;
            }
            var key = levelled.group(1);
            int level;
            try {
                level = Integer.parseInt(levelled.group(2));
            } catch (NumberFormatException e) {
                throw failure(entry);
            }
            if (key.equalsIgnoreCase(COUNT)) {
                if (count != null || level < 0) throw failure(entry);
                count = level;
            } else if (key.equalsIgnoreCase(DURABILITY)) {
                if (durability != null || level < 1 || level > ItemCriteria.MAX_DURABILITY) throw failure(entry);
                durability = level;
            } else if (level < 1 || enchantments.stream().anyMatch(other -> sameKey(other.enchantment(), key))) {
                throw failure(entry);
            } else {
                enchantments.add(new ItemCriteria.Required(key, level));
            }
        }
        if (items.isEmpty()) throw failure(text.strip());
        try {
            return new ItemCriteria(
                    items, count == null ? 0 : count, enchantments, potion, durability == null ? 0 : durability);
        } catch (IllegalArgumentException e) {
            throw failure(text.strip());
        }
    }

    /**
     * Writes criteria in the text form, so they can be shown and entered again.
     *
     * @param criteria the criteria
     * @return the text, such as {@code #logs bamboo_block enchants:2 sharpness:3 durability:80}
     */
    public static String format(ItemCriteria criteria) {
        List<String> entries = new ArrayList<>();
        for (var item : criteria.items()) {
            entries.add(ItemCriteria.isTag(item) ? "#" + shortKey(item.substring(1)) : item.toLowerCase(Locale.ROOT));
        }
        if (criteria.minEnchantments() > criteria.enchantments().size()) {
            entries.add(COUNT + ":" + criteria.minEnchantments());
        }
        for (var required : criteria.enchantments()) {
            entries.add(shortKey(required.enchantment()) + ":" + required.minLevel());
        }
        var potion = criteria.potion();
        if (potion != null) entries.add(POTION + ":" + shortKey(potion));
        if (criteria.minDurability() > 0) entries.add(DURABILITY + ":" + criteria.minDurability());
        return String.join(" ", entries);
    }

    private static List<String> entries(String text) {
        return SEPARATOR
                .splitAsStream(text.strip())
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    private static boolean sameKey(String normalized, String key) {
        return new ItemCriteria.Required(key, 1).enchantment().equals(normalized);
    }

    private static String shortKey(String key) {
        return key.startsWith("minecraft:") ? key.substring("minecraft:".length()) : key;
    }

    private static CommandFailure failure(String entry) {
        return CommandFailure.actionBar("order.criteria.error.syntax", Replacement.create("entry", entry));
    }
}
