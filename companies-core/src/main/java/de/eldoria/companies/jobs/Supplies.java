/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.repository.AJobData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The materials of a build job whose owner supplies them: what its blueprint takes, as {@link BlueprintItems} counts
 * it with the fluids, what its stock holds and what is still missing, and handing items in from an inventory or from
 * the owner's item returns, only while the job is a supplied draft. Only plain items count, as a block placed from a
 * renamed or enchanted item is the same block; a water or lava bucket handed in gives an empty bucket back. Nothing is
 * taken beyond what is missing. The methods read the database; call them off the main thread.
 */
public final class Supplies {
    private final AJobData jobs;
    private final Blueprints blueprints;

    /**
     * @param jobs       the jobs and their stock
     * @param blueprints the blueprints of build jobs
     */
    public Supplies(AJobData jobs, Blueprints blueprints) {
        this.jobs = jobs;
        this.blueprints = blueprints;
    }

    /**
     * @param job id of a build job
     * @return the items and buckets its blueprint takes, by type; empty without a blueprint
     */
    public Map<Material, Integer> needs(int job) {
        Map<Material, Integer> needs = new LinkedHashMap<>();
        blueprints.of(job).ifPresent(blueprint -> {
            var counted = BlueprintItems.of(blueprint);
            needs.putAll(counted.items());
            counted.fluids().forEach((type, amount) -> needs.merge(type, amount, Integer::sum));
        });
        return needs;
    }

    /**
     * @param job id of a build job
     * @return what its stock still lacks of what its blueprint takes, by type, without the types it holds enough of
     */
    public Map<Material, Integer> missing(int job) {
        var stock = jobs.stock(job);
        Map<Material, Integer> missing = new LinkedHashMap<>();
        needs(job).forEach((type, amount) -> {
            var left = amount - stock.getOrDefault(type.name(), 0);
            if (left > 0) missing.put(type, left);
        });
        return missing;
    }

    /**
     * What came of handing items in.
     *
     * @param stored whether the job took items, false when it is no supplied draft any more or they could not be
     *     stored; then everything goes back
     * @param taken  what went into the stock, by type
     * @param back   what the player gets back: what was not needed, and an empty bucket for each full one taken
     */
    public record Handed(boolean stored, Map<Material, Integer> taken, List<ItemStack> back) {}

    /**
     * Puts what a supplied draft still lacks of the items into its stock.
     *
     * @param job   id of the draft
     * @param items the items handed in
     * @return what was taken and what goes back
     */
    public Handed handIn(int job, List<ItemStack> items) {
        Map<Material, Integer> offered = new LinkedHashMap<>();
        for (var item : items) {
            if (item != null && !item.getType().isAir() && plain(item)) {
                offered.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }
        var stored = supply(job, offered);
        if (stored.isEmpty()) return new Handed(false, Map.of(), List.copyOf(items));
        var left = new LinkedHashMap<>(stored.get());
        List<ItemStack> back = new ArrayList<>();
        for (var item : items) {
            if (item == null || item.getType().isAir()) continue;
            var type = item.getType();
            var take = plain(item) ? Math.min(left.getOrDefault(type, 0), item.getAmount()) : 0;
            if (take > 0) left.merge(type, -take, Integer::sum);
            if (take < item.getAmount()) back.add(item.asQuantity(item.getAmount() - take));
            if (take > 0 && bucket(type)) back.add(new ItemStack(Material.BUCKET, take));
        }
        return new Handed(true, stored.get(), back);
    }

    /**
     * Takes what a supplied draft still lacks from its owner's item returns, both kinds, into its stock. Whatever the
     * draft no longer takes goes back to the kind it came from, and an empty bucket for each full one taken to the
     * owner's.
     *
     * @param owner  the owner of the job
     * @param job    id of the draft
     * @param orders the item returns
     * @return what was taken, by type; empty when it is no supplied draft
     */
    public Map<Material, Integer> fromReturns(UUID owner, int job, AOrderData orders) {
        var missing = missing(job);
        Map<Material, Integer> out = new LinkedHashMap<>();
        List<Took> took = new ArrayList<>();
        for (var kind : ReturnKind.values()) {
            for (var stored : orders.retrieveItemReturns(owner, kind).asFuture().join()) {
                var type = stored.stack().getType();
                var wanted = missing.getOrDefault(type, 0);
                if (wanted <= 0 || !plain(stored.stack())) continue;
                var take = Math.min(wanted, stored.amount());
                if (!Boolean.TRUE.equals(
                        orders.submitItemReturnTake(stored, take).asFuture().join())) continue;
                missing.put(type, wanted - take);
                out.merge(type, take, Integer::sum);
                took.add(new Took(kind, stored.stack(), take));
            }
        }
        if (out.isEmpty()) return Map.of();
        var taken = supply(job, out).orElse(Map.of());
        Map<Material, Integer> kept = new LinkedHashMap<>(taken);
        for (var one : took) {
            var type = one.stack().getType();
            var used = Math.min(one.amount(), kept.getOrDefault(type, 0));
            kept.merge(type, -used, Integer::sum);
            if (used < one.amount()) giveBack(orders, owner, one.kind(), one.stack(), one.amount() - used);
        }
        var buckets = taken.entrySet().stream()
                .filter(entry -> bucket(entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
        if (buckets > 0) giveBack(orders, owner, ReturnKind.OWNER, new ItemStack(Material.BUCKET), buckets);
        return taken;
    }

    /**
     * Items taken out of the item returns.
     *
     * @param kind   the kind of returns they came from
     * @param stack  the item
     * @param amount how many
     */
    private record Took(ReturnKind kind, ItemStack stack, int amount) {}

    private Optional<Map<Material, Integer>> supply(int job, Map<Material, Integer> offered) {
        Map<String, Integer> needs = new LinkedHashMap<>();
        needs(job).forEach((type, amount) -> needs.put(type.name(), amount));
        Map<String, Integer> byName = new LinkedHashMap<>();
        offered.forEach((type, amount) -> byName.put(type.name(), amount));
        return jobs.supply(job, needs, byName).map(taken -> {
            Map<Material, Integer> byType = new LinkedHashMap<>();
            taken.forEach((name, amount) -> byType.put(Material.valueOf(name), amount));
            return byType;
        });
    }

    private static void giveBack(AOrderData orders, UUID owner, ReturnKind kind, ItemStack stack, int amount) {
        orders.submitItemReturn(owner, kind, stack, amount).asFuture().join();
    }

    private static boolean plain(ItemStack item) {
        return item.isSimilar(new ItemStack(item.getType()));
    }

    private static boolean bucket(Material type) {
        return type == Material.WATER_BUCKET || type == Material.LAVA_BUCKET;
    }
}
