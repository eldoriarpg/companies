/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.market;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.configuration.elements.ordersettings.MarketSettings;
import de.eldoria.companies.data.repository.ADashboardData;
import de.eldoria.companies.data.repository.AHistoryData;
import de.eldoria.companies.data.wrapper.dashboard.MarketDay;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The market's price history: one row per material and day, from the orders companies delivered that day, kept for
 * {@code market.historyDays} of {@code order.yml}. Exact lines are left out, as in the market list; solo orders write
 * no company entry and are not part of it. No owner or company is stored.
 * <p>
 * The primary node records it and notes the day it recorded through. Each run computes the days from the one before
 * that note, so a late write near midnight is still counted, up to today; the first run reaches back as far as the
 * retention. A day is never computed again once the order history may have purged part of it ({@code historyDays} of
 * {@code config.yml}), so purging the order history never erases stored days. Days are replaced as a whole, and runs
 * never overlap.
 * <p>
 * Reading compares periods: the change of a material is that of its average unit price over the last seven days
 * against the seven days before, weighted by orders. Only days that stand on at least {@code market.minOrders} orders
 * themselves count, the same days whose prices the history shows, so the change reveals nothing a shown day does not.
 * A period without such a day has no change.
 */
public final class MarketHistory {
    /**
     * The days of each period a change compares.
     */
    public static final int CHANGE_DAYS = 7;

    private final Sources sources;
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * @param sources where the history is read from and stored
     */
    public MarketHistory(Sources sources) {
        this.sources = sources;
    }

    /**
     * Records the days not recorded yet, then removes the days beyond the retention. A run that finds another one still
     * busy does nothing. A day that cannot be stored ends the run without moving the note forward, so the next run
     * starts there again; other failures are logged and the next run tries again.
     */
    public void record() {
        if (!running.compareAndSet(false, true)) return;
        try {
            var today = today();
            var oldest = today.minusDays(sources.settings().get().historyDays() - 1L);
            var complete = today.minusDays(sources.orderHistoryDays().get()).plusDays(1);
            var from = latest(
                    sources.data()
                            .marketRecordedThrough()
                            .map(day -> day.minusDays(1))
                            .orElse(oldest),
                    oldest,
                    complete);
            for (var day = from; !day.isAfter(today); day = day.plusDays(1)) {
                var entries = sources.history()
                        .retrieveCompanyEntries(
                                HistoryOutcome.DELIVERED,
                                day.atStartOfDay(),
                                day.plusDays(1).atStartOfDay())
                        .join();
                if (!sources.data().saveMarketDay(day, aggregate(day, entries))) {
                    sources.logger().warning("Could not store the market history of " + day + ".");
                    if (day.isAfter(from)) sources.data().saveMarketRecordedThrough(day.minusDays(1));
                    return;
                }
            }
            sources.data().saveMarketRecordedThrough(today);
            sources.data().purgeMarketHistory(oldest);
        } catch (RuntimeException e) {
            sources.logger().log(Level.WARNING, "Could not record the market history.", e);
        } finally {
            running.set(false);
        }
    }

    private static LocalDate latest(LocalDate first, LocalDate... others) {
        var latest = first;
        for (var other : others) if (other.isAfter(latest)) latest = other;
        return latest;
    }

    /**
     * @return today in the server's time zone
     */
    public LocalDate today() {
        return LocalDate.now(sources.clock().withZone(sources.zone()));
    }

    /**
     * Adds up the orders delivered on one day per material, without exact lines.
     *
     * @param day     the day
     * @param entries the company entries of the orders delivered that day
     * @return one row per material, ordered by material
     */
    public static List<MarketDay> aggregate(LocalDate day, List<HistoryEntry> entries) {
        Map<String, Totals> totals = new HashMap<>();
        for (var entry : entries) {
            for (var line : entry.snapshot().lines()) {
                if (line.amount() <= 0 || line.exact()) continue;
                totals.computeIfAbsent(line.template().getType().name(), material -> new Totals())
                        .add(entry.order(), line.amount(), line.price() / line.amount());
            }
        }
        return totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(total -> total.getValue().toDay(day, total.getKey()))
                .toList();
    }

    /**
     * The change of each material's average unit price over the last {@value #CHANGE_DAYS} days against the
     * {@value #CHANGE_DAYS} days before.
     *
     * @param days      the rows of at least the last {@code 2 * CHANGE_DAYS} days
     * @param today     the last day of the recent period
     * @param minOrders the orders a day needs to count
     * @return the change in percent per material, for materials both periods have a day with enough orders for
     */
    public static Map<String, Double> changes(List<MarketDay> days, LocalDate today, int minOrders) {
        var recentStart = today.minusDays(CHANGE_DAYS - 1L);
        var previousStart = recentStart.minusDays(CHANGE_DAYS);
        Map<String, Period[]> periods = new HashMap<>();
        for (var day : days) {
            if (day.orders() < minOrders
                    || day.day().isBefore(previousStart)
                    || day.day().isAfter(today)) continue;
            var both = periods.computeIfAbsent(day.material(), material -> new Period[] {new Period(), new Period()});
            both[day.day().isBefore(recentStart) ? 0 : 1].add(day);
        }
        Map<String, Double> changes = new HashMap<>();
        periods.forEach((material, both) -> {
            if (both[0].orders == 0 || both[1].orders == 0) return;
            var previous = both[0].average();
            if (previous <= 0) return;
            changes.put(material, (both[1].average() - previous) / previous * 100);
        });
        return changes;
    }

    /**
     * The history of one material for a chart, a point per day with completed orders. The price of a day that stands
     * on fewer than {@code minOrders} orders is left out, so a single order cannot be traced back; the lowest and
     * highest price of a day are never shown, since with few orders they would name single orders.
     *
     * @param material  the material name
     * @param days      how many days back, today included
     * @param minOrders the orders a day needs for its prices to show
     * @return the points, oldest first
     */
    public List<Point> points(String material, int days, int minOrders) {
        var from = today().minusDays(Math.max(1, days) - 1L);
        return sources.data().marketHistory(material, from).stream()
                .sorted(Comparator.comparing(MarketDay::day))
                .map(day ->
                        new Point(day.day(), day.orders(), day.items(), day.orders() >= minOrders ? day.avg() : null))
                .toList();
    }

    /**
     * The changes of every material, as {@link #changes(List, LocalDate, int)} computes them.
     *
     * @return the change in percent per material
     */
    public Map<String, Double> changes() {
        var today = today();
        var days = sources.data().marketDays(today.minusDays(2L * CHANGE_DAYS - 1));
        return changes(days, today, sources.settings().get().minOrders());
    }

    /**
     * One day of a material's history.
     *
     * @param day    the day
     * @param orders the orders completed that day
     * @param items  the items in them
     * @param avg    the average unit price, absent with too few orders
     */
    public record Point(
            LocalDate day, int orders, long items, @Nullable Double avg) {}

    /**
     * What the history is recorded from and stored in.
     *
     * @param data             the market history table
     * @param history          the order history the days are computed from
     * @param settings         the retention and the orders a price needs
     * @param orderHistoryDays how many days the order history is kept, after which its entries are purged
     * @param zone             the server's time zone, which decides where a day ends
     * @param clock            the clock
     * @param logger           logs failed runs
     */
    public record Sources(
            ADashboardData data,
            AHistoryData history,
            Supplier<MarketSettings> settings,
            Supplier<Integer> orderHistoryDays,
            ZoneId zone,
            Clock clock,
            Logger logger) {}

    /**
     * The running totals of one material on one day.
     */
    private static final class Totals {
        private final List<Integer> orders = new ArrayList<>();
        private long items;
        private double sum;
        private int lines;
        private double min = Double.MAX_VALUE;
        private double max;

        void add(int order, int amount, double unitPrice) {
            if (!orders.contains(order)) orders.add(order);
            items += amount;
            sum += unitPrice;
            lines++;
            min = Math.min(min, unitPrice);
            max = Math.max(max, unitPrice);
        }

        MarketDay toDay(LocalDate day, String material) {
            return new MarketDay(day, material, orders.size(), items, sum / lines, min, max);
        }
    }

    /**
     * The days of one material in one period of a change.
     */
    private static final class Period {
        private int orders;
        private double weighted;

        void add(MarketDay day) {
            orders += day.orders();
            weighted += day.avg() * day.orders();
        }

        double average() {
            return orders == 0 ? 0 : weighted / orders;
        }
    }
}
