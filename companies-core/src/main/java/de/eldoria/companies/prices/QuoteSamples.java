/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopQuote;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.data.wrapper.price.QuoteGroup;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sums up a sample of the offers of the shops per source, material, side and origin, leaving out offers of items that
 * are not plain and offers without a valid price.
 */
public final class QuoteSamples {
    private QuoteSamples() {}

    /**
     * @param sampled   the offers per source id
     * @param sampledAt when they were sampled
     * @return one group per source, material, side and origin with offers
     */
    public static List<QuoteGroup> groups(Map<String, Collection<ShopQuote>> sampled, LocalDateTime sampledAt) {
        Map<Key, Offers> groups = new LinkedHashMap<>();
        sampled.forEach((source, quotes) -> {
            for (var quote : quotes) {
                if (quote == null || quote.side() == null || quote.origin() == null) continue;
                if (!ShopItems.valid(quote.amount(), quote.price())) continue;
                var material = ShopItems.material(quote.item());
                if (material.isEmpty()) continue;
                groups.computeIfAbsent(
                                new Key(source, material.get(), quote.side(), quote.origin()), key -> new Offers())
                        .add(quote.price() / quote.amount(), quote.amount());
            }
        });
        List<QuoteGroup> result = new ArrayList<>();
        groups.forEach((key, offers) -> result.add(offers.group(key, sampledAt)));
        return result;
    }

    private record Key(String source, String material, ShopSide side, ShopOrigin origin) {}

    private static final class Offers {
        private final List<Double> units = new ArrayList<>();
        private long items;

        void add(double unit, int amount) {
            units.add(unit);
            items += amount;
        }

        QuoteGroup group(Key key, LocalDateTime sampledAt) {
            var sorted = units.stream().sorted().toList();
            var middle = sorted.size() / 2;
            var median =
                    sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
            return new QuoteGroup(
                    key.source(),
                    key.material(),
                    key.side(),
                    key.origin(),
                    sorted.size(),
                    items,
                    median,
                    sorted.getFirst(),
                    sorted.getLast(),
                    sampledAt);
        }
    }
}
