/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import de.eldoria.companies.components.price.PriceBasis;
import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.configuration.elements.prices.PriceSettings;
import de.eldoria.companies.data.wrapper.price.Baseline;
import de.eldoria.companies.data.wrapper.price.QuoteGroup;
import de.eldoria.companies.data.wrapper.price.TradeDay;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Works out the baseline of every material from the trades and offers of the shops.
 * <p>
 * Per material and side the most trusted kind of price with enough data is taken:
 * <ol>
 *   <li>trades, at least {@code minTrades}: the median of the daily averages, each day of each source and origin
 *       weighted by its number of trades</li>
 *   <li>otherwise listings of players, at least {@code minListings}: the median of the sampled medians, weighted by
 *       their number of offers</li>
 *   <li>otherwise the prices of admins and the server: their median, weighted the same way</li>
 * </ol>
 * The baseline is the midpoint of the sell and the buy side when both are known, otherwise the known side times its
 * factor. Where the server prices a side, the baseline never falls below its sell price nor rises above its buy price.
 * The sources of a baseline are those of the prices its sides came from, and those whose server prices held it in.
 * Sources switched off are left out, and of a source that counts only admins and the server, so are its players.
 */
public final class Baselines {
    private Baselines() {}

    /**
     * @param trades   the trades of the days that count
     * @param quotes   the samples recent enough to count
     * @param settings the settings of shop prices
     * @return the baseline of every material some shop prices, by material name
     */
    public static List<Baseline> compute(List<TradeDay> trades, List<QuoteGroup> quotes, PriceSettings settings) {
        Map<String, Evidence> materials = new TreeMap<>();
        for (var trade : trades) {
            if (!counts(trade.source(), trade.origin(), settings) || trade.trades() <= 0 || trade.items() <= 0)
                continue;
            materials
                    .computeIfAbsent(trade.material(), key -> new Evidence())
                    .side(trade.side())
                    .trades
                    .add(new Weighted(trade.unit(), trade.trades(), trade.source()));
        }
        for (var quote : quotes) {
            if (!counts(quote.source(), quote.origin(), settings) || quote.quotes() <= 0) continue;
            var side = materials
                    .computeIfAbsent(quote.material(), key -> new Evidence())
                    .side(quote.side());
            var weighted = new Weighted(quote.medianUnit(), quote.quotes(), quote.source());
            if (quote.origin() == ShopOrigin.PLAYER) side.listings.add(weighted);
            else side.server.add(weighted);
        }
        List<Baseline> baselines = new ArrayList<>();
        materials.forEach((material, evidence) -> {
            var baseline = evidence.baseline(material, settings);
            if (baseline != null) baselines.add(baseline);
        });
        return baselines;
    }

    private static boolean counts(String source, ShopOrigin origin, PriceSettings settings) {
        if (!settings.sourceEnabled(source)) return false;
        return origin != ShopOrigin.PLAYER || !settings.adminOnly(source);
    }

    /**
     * The weighted median: the value at which half of the weight lies below and half above, the lower of two when
     * the halves meet between them.
     *
     * @param values the values with their weights
     * @return the median, null when there is no weight
     */
    static @Nullable Double median(List<Weighted> values) {
        var total = values.stream().mapToLong(Weighted::weight).sum();
        if (total <= 0) return null;
        var sorted = values.stream()
                .sorted(Comparator.comparingDouble(Weighted::value))
                .toList();
        long seen = 0;
        for (var value : sorted) {
            seen += value.weight();
            if (seen * 2 >= total) return value.value();
        }
        return sorted.getLast().value();
    }

    /**
     * A value with its weight.
     *
     * @param value  the value
     * @param weight how much it counts
     * @param source id of the source it comes from
     */
    record Weighted(double value, long weight, String source) {}

    private record Price(double unit, PriceBasis basis, Set<String> sources) {}

    private static final class Evidence {
        private final Side sell = new Side();
        private final Side buy = new Side();

        Side side(ShopSide side) {
            return side == ShopSide.SELL ? sell : buy;
        }

        @Nullable
        Baseline baseline(String material, PriceSettings settings) {
            var sellPrice = sell.price(settings);
            var buyPrice = buy.price(settings);
            if (sellPrice == null && buyPrice == null) return null;
            double unit;
            if (sellPrice != null && buyPrice != null) unit = (sellPrice.unit() + buyPrice.unit()) / 2;
            else if (sellPrice != null) unit = sellPrice.unit() * settings.sellFactor();
            else unit = buyPrice.unit() * settings.buyFactor();
            var sources = new TreeSet<String>();
            if (sellPrice != null) sources.addAll(sellPrice.sources());
            if (buyPrice != null) sources.addAll(buyPrice.sources());
            var floor = median(sell.server);
            var ceiling = median(buy.server);
            var clampable = floor == null || ceiling == null || floor <= ceiling;
            if (clampable && floor != null && unit < floor) {
                unit = floor;
                sell.server.forEach(value -> sources.add(value.source()));
            }
            if (clampable && ceiling != null && unit > ceiling) {
                unit = ceiling;
                buy.server.forEach(value -> sources.add(value.source()));
            }
            if (unit <= 0) return null;
            var basis = weaker(sellPrice, buyPrice);
            return new Baseline(
                    material,
                    sellPrice == null ? null : sellPrice.unit(),
                    buyPrice == null ? null : buyPrice.unit(),
                    unit,
                    basis,
                    List.copyOf(sources));
        }

        private static PriceBasis weaker(@Nullable Price sell, @Nullable Price buy) {
            var bases = new TreeSet<PriceBasis>();
            if (sell != null) bases.add(sell.basis());
            if (buy != null) bases.add(buy.basis());
            return bases.last();
        }
    }

    private static final class Side {
        private final List<Weighted> trades = new ArrayList<>();
        private final List<Weighted> listings = new ArrayList<>();
        private final List<Weighted> server = new ArrayList<>();

        @Nullable
        Price price(PriceSettings settings) {
            if (weight(trades) >= settings.minTrades()) return price(trades, PriceBasis.TRADES);
            if (weight(listings) >= settings.minListings()) return price(listings, PriceBasis.LISTINGS);
            if (!server.isEmpty()) return price(server, PriceBasis.SERVER);
            return null;
        }

        private static @Nullable Price price(List<Weighted> values, PriceBasis basis) {
            var median = median(values);
            if (median == null || median <= 0) return null;
            var sources = values.stream().map(Weighted::source).collect(Collectors.toCollection(TreeSet::new));
            return new Price(median, basis, sources);
        }

        private static long weight(List<Weighted> values) {
            return values.stream().mapToLong(Weighted::weight).sum();
        }
    }
}
