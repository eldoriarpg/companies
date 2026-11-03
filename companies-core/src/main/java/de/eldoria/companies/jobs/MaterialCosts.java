/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * What the blocks of a blueprint cost: for each item the blocks are placed with, as {@link BlueprintItems} counts them,
 * the items needed times the price of one, the price Companies goes by for the material (orders, or the shops' baseline
 * below enough orders). Blocks no item places are listed as unpriced. Fluids are left out, as companies never have to
 * pour them. The cost of a job is kept for {@value #KEEP_MINUTES} minutes. Blocks; call it off the main thread.
 */
public final class MaterialCosts {
    /**
     * How long the cost of a job is kept before its prices are read again.
     */
    public static final int KEEP_MINUTES = 10;

    private final Blueprints blueprints;
    private final ToDoubleFunction<Material> prices;
    private final Function<Blueprint.Want, @Nullable Material> items;
    private final Cache<Integer, Cost> kept = CacheBuilder.newBuilder()
            .expireAfterWrite(KEEP_MINUTES, TimeUnit.MINUTES)
            .build();

    /**
     * @param blueprints the blueprints of build jobs
     * @param prices     the price of one item of a material, 0 when none is known
     */
    public MaterialCosts(Blueprints blueprints, ToDoubleFunction<Material> prices) {
        this(blueprints, prices, BlueprintItems::placedWith);
    }

    /**
     * @param blueprints the blueprints of build jobs
     * @param prices     the price of one item of a material, 0 when none is known
     * @param items      the item a block is placed with, null for a block no item places
     */
    MaterialCosts(
            Blueprints blueprints,
            ToDoubleFunction<Material> prices,
            Function<Blueprint.Want, @Nullable Material> items) {
        this.blueprints = blueprints;
        this.prices = prices;
        this.items = items;
    }

    /**
     * What the blocks of a blueprint cost.
     *
     * @param total    the cost of the blocks with a known price
     * @param unpriced the block types without a known price, which the total leaves out
     */
    public record Cost(double total, List<Material> unpriced) {
        /**
         * Keeps the unpriced types unchangeable.
         */
        public Cost {
            unpriced = List.copyOf(unpriced);
        }

        /**
         * @param price what a build job pays
         * @return whether it pays less than its blocks cost
         */
        public boolean above(double price) {
            return price < total;
        }
    }

    /**
     * @param blueprint a blueprint
     * @return what its blocks cost
     */
    public Cost of(Blueprint blueprint) {
        var needs = BlueprintItems.of(blueprint, items);
        List<Material> unpriced = new ArrayList<>(needs.byHand());
        unpriced.addAll(needs.unmakeable());
        var total = 0.0;
        for (var entry : needs.items().entrySet()) {
            var price = prices.applyAsDouble(entry.getKey());
            if (price > 0) {
                total += price * entry.getValue();
            } else if (!unpriced.contains(entry.getKey())) {
                unpriced.add(entry.getKey());
            }
        }
        return new Cost(total, unpriced);
    }

    /**
     * @param items items by type, such as the blocks an area job digs or fills with
     * @return what they are worth; types that are no item or have no known price are unpriced
     */
    public Cost value(Map<Material, Integer> items) {
        List<Material> unpriced = new ArrayList<>();
        var total = 0.0;
        for (var entry : items.entrySet()) {
            var price = entry.getKey().isItem() ? prices.applyAsDouble(entry.getKey()) : 0;
            if (price > 0) {
                total += price * entry.getValue();
            } else {
                unpriced.add(entry.getKey());
            }
        }
        return new Cost(total, unpriced);
    }

    /**
     * @param job id of a job
     * @return what the blocks of its blueprint cost, empty for a job without one
     */
    public Optional<Cost> of(int job) {
        var known = kept.getIfPresent(job);
        if (known != null) return Optional.of(known);
        var cost = blueprints.of(job).map(this::of);
        cost.ifPresent(found -> kept.put(job, found));
        return cost;
    }
}
