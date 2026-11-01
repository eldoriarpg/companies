/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.views;

import de.eldoria.companies.data.repository.AHistoryData;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.SavedItemService;
import de.eldoria.companies.orders.criteria.CriteriaText;
import de.eldoria.companies.orders.definition.ItemDefinitions;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * What players can make orders of on the dashboard: the materials the server lets them order, with what survival can
 * give each (enchantments up to their highest level, potion types, dye colours, armour trims), and the items a player
 * can order again exactly, their saved items and the exact lines of their own earlier orders.
 * <p>
 * Items are named by key as {@code orders.create} takes them: a material key, {@code saved:<id>} or
 * {@code earlier:<history entry>:<line>}. An earlier item is the player's own only.
 */
public final class OrderCatalogue {
    /**
     * How many earlier orders are looked through for exact items.
     */
    public static final int EARLIER_ORDERS = 50;

    private static final String SAVED = "saved:";
    private static final String EARLIER = "earlier:";

    private final Function<ItemStack, Optional<OrderDraftService.RequestedItem>> requests;
    private final SavedItemService saved;
    private final AHistoryData history;

    /**
     * @param requests turns an item into what an order line requests, empty when it cannot be ordered
     * @param saved    the players' saved items
     * @param history  the order history the earlier items come from
     */
    public OrderCatalogue(
            Function<ItemStack, Optional<OrderDraftService.RequestedItem>> requests,
            SavedItemService saved,
            AHistoryData history) {
        this.requests = requests;
        this.saved = saved;
        this.history = history;
    }

    /**
     * Builds the catalogue of the server. It depends on the server's version, data packs and item rules only, so it is
     * cached by the caller.
     *
     * @return the catalogue
     */
    public Payloads.Catalogue catalogue() {
        var enchantments = entries(RegistryKey.ENCHANTMENT);
        List<Payloads.CatalogueMaterial> materials = new ArrayList<>();
        for (var material : Material.values()) {
            if (material.isLegacy() || !material.isItem() || material.isAir()) continue;
            var stack = new ItemStack(material);
            if (requests.apply(stack).isEmpty()) continue;
            var meta = stack.getItemMeta();
            var book = meta instanceof EnchantmentStorageMeta;
            materials.add(new Payloads.CatalogueMaterial(
                    Views.item(material, 1),
                    enchantments.stream()
                            .filter(enchantment -> book || enchantment.canEnchantItem(stack))
                            .map(enchantment -> enchantment.getKey().toString())
                            .toList(),
                    meta instanceof PotionMeta,
                    meta instanceof LeatherArmorMeta,
                    meta instanceof ArmorMeta,
                    material.getMaxDurability() > 0));
        }
        return new Payloads.Catalogue(
                materials,
                enchantments.stream()
                        .map(enchantment -> new Payloads.CatalogueEnchantment(
                                enchantment.getKey().toString(),
                                PlainText.keyName(enchantment.getKey()),
                                enchantment.getMaxLevel(),
                                enchantments.stream()
                                        .filter(other -> other != enchantment && other.conflictsWith(enchantment))
                                        .map(other -> other.getKey().toString())
                                        .toList()))
                        .toList(),
                names(entries(RegistryKey.POTION).stream()
                        .filter(ItemDefinitions::brewable)
                        .toList()),
                ItemDefinitions.dyes().stream()
                        .map(dye -> {
                            var key = dye.name().toLowerCase(Locale.ROOT);
                            return new Payloads.CatalogueDye(
                                    key, PlainText.keyName(NamespacedKey.minecraft(key)), hex(dye));
                        })
                        .toList(),
                names(entries(RegistryKey.TRIM_PATTERN)),
                names(entries(RegistryKey.TRIM_MATERIAL)),
                tags(materials));
    }

    /**
     * The item tags of the server whose items can all be ordered, by key.
     *
     * @param materials the orderable materials
     * @return the tags
     */
    private static List<Payloads.CatalogueTag> tags(List<Payloads.CatalogueMaterial> materials) {
        var orderable =
                materials.stream().map(material -> material.item().material()).collect(Collectors.toSet());
        List<Payloads.CatalogueTag> tags = new ArrayList<>();
        for (var tag : Bukkit.getTags(Tag.REGISTRY_ITEMS, Material.class)) {
            var members = tag.getValues().stream()
                    .map(material -> material.getKey().toString())
                    .sorted()
                    .toList();
            if (members.isEmpty() || !orderable.containsAll(members)) continue;
            var entry = "#" + tag.getKey();
            tags.add(new Payloads.CatalogueTag(entry, CriteriaText.itemName(entry), members));
        }
        tags.sort(Comparator.comparing(Payloads.CatalogueTag::key));
        return tags;
    }

    /**
     * The items a player can order again exactly.
     *
     * @param player the player
     * @return the saved items and the exact items of earlier orders
     */
    public CompletableFuture<Payloads.KnownItems> known(UUID player) {
        return saved.items(player)
                .thenCombine(
                        history.retrieveOwnerHistory(player, 1, EARLIER_ORDERS),
                        (items, orders) -> new Payloads.KnownItems(
                                items.stream()
                                        .map(item -> known(SAVED + item.id(), item.name(), item.stack()))
                                        .toList(),
                                earlier(orders)));
    }

    /**
     * Finds an exact item of a player by the key the catalogue named it with.
     *
     * @param player the player
     * @param ref    {@code saved:<id>} or {@code earlier:<history entry>:<line>}
     * @return the item, empty when the key names none of the player's items
     */
    public CompletableFuture<Optional<ItemStack>> find(UUID player, String ref) {
        try {
            if (ref.startsWith(SAVED)) {
                var id = Integer.parseInt(ref.substring(SAVED.length()));
                return saved.item(player, id).thenApply(item -> item.map(found -> found.stack()));
            }
            if (ref.startsWith(EARLIER)) {
                var parts = ref.substring(EARLIER.length()).split(":");
                if (parts.length != 2) return CompletableFuture.completedFuture(Optional.empty());
                var entry = Long.parseLong(parts[0]);
                var line = Integer.parseInt(parts[1]);
                return history.retrieveEntry(entry)
                        .thenApply(found -> found.filter(order -> player.equals(order.owner()))
                                .flatMap(order -> line(order, line)));
            }
        } catch (NumberFormatException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    private static Optional<ItemStack> line(HistoryEntry order, int line) {
        var lines = order.snapshot().lines();
        if (line < 0 || line >= lines.size() || !lines.get(line).exact()) return Optional.empty();
        return Optional.of(lines.get(line).template().asOne());
    }

    private static List<Payloads.KnownItem> earlier(List<HistoryEntry> orders) {
        List<Payloads.KnownItem> items = new ArrayList<>();
        List<ItemStack> seen = new ArrayList<>();
        for (var order : orders) {
            var lines = order.snapshot().lines();
            for (var index = 0; index < lines.size(); index++) {
                var line = lines.get(index);
                if (!line.exact() || seen.stream().anyMatch(item -> item.isSimilar(line.template()))) continue;
                seen.add(line.template());
                items.add(known(EARLIER + order.id() + ":" + index, order.name(), line.template()));
            }
        }
        return items;
    }

    private static Payloads.KnownItem known(String ref, String label, ItemStack stack) {
        return new Payloads.KnownItem(ref, label, Views.item(stack, 1), details(stack));
    }

    /**
     * What an item carries, as short lines: its enchantments with levels, its potion type, its dye and its trim.
     *
     * @param stack the item
     * @return the lines
     */
    public static List<String> details(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        stack.getEnchantments().forEach((enchantment, level) -> lines.add(enchantment(enchantment, level)));
        var meta = stack.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta book) {
            book.getStoredEnchants().forEach((enchantment, level) -> lines.add(enchantment(enchantment, level)));
        }
        if (meta instanceof PotionMeta potion) {
            var type = potion.getBasePotionType();
            if (type != null) lines.add(PlainText.keyName(type.getKey()));
        }
        if (meta instanceof LeatherArmorMeta leather) {
            Arrays.stream(DyeColor.values())
                    .filter(dye -> dye.getColor().equals(leather.getColor()))
                    .findFirst()
                    .ifPresent(dye -> lines.add(
                            PlainText.keyName(NamespacedKey.minecraft(dye.name().toLowerCase(Locale.ROOT)))));
        }
        if (meta instanceof ArmorMeta armor && armor.getTrim() instanceof ArmorTrim trim) {
            lines.add(PlainText.keyName(trim.getPattern().getKey()) + " ("
                    + PlainText.keyName(trim.getMaterial().getKey()) + ")");
        }
        return lines;
    }

    private static String enchantment(Enchantment enchantment, int level) {
        var name = PlainText.keyName(enchantment.getKey());
        return enchantment.getMaxLevel() == 1 ? name : name + " " + level;
    }

    private static String hex(DyeColor dye) {
        return String.format(Locale.ROOT, "#%06x", dye.getColor().asRGB());
    }

    private static <T extends Keyed> List<T> entries(RegistryKey<T> key) {
        var registry = RegistryAccess.registryAccess().getRegistry(key);
        return StreamSupport.stream(registry.spliterator(), false)
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .toList();
    }

    private static <T extends Keyed> List<Payloads.CatalogueEntry> names(List<T> entries) {
        return entries.stream()
                .map(entry -> new Payloads.CatalogueEntry(entry.getKey().toString(), PlainText.keyName(entry.getKey())))
                .toList();
    }
}
