/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.AStatsData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.stats.PayoutRecord;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.OrderPaymentEvent;
import de.eldoria.companies.events.order.SoloOrderDoneEvent;
import de.eldoria.companies.events.order.SoloOrderPaymentEvent;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntToDoubleFunction;
import java.util.logging.Level;

/**
 * Completes orders whose items were all delivered.
 * <p>
 * Completing marks the order delivered, which succeeds once per order, so a completion that lost a race does nothing.
 * Settling a delivered order fires {@link OrderDoneEvent}, pays every worker their share minus the tax of the
 * company's level and minus the company's treasury share, hands the fee and the taxes to the collector, credits the
 * treasury shares to the company in one transaction, writes one payout record per worker to the ledger and records
 * the company's history entry. A payment whose {@link OrderPaymentEvent} was cancelled is not paid and
 * is written to the ledger as not paid; it is neither taxed nor shared. Events are fired off the main thread, as they are asynchronous.
 * <p>
 * An order a player worked on alone fires {@link SoloOrderDoneEvent} and one {@link SoloOrderPaymentEvent} per worker
 * instead. Its payout is taxed by the solo tax, nothing goes to a treasury, its ledger records name no company and no
 * company history entry is written.
 */
public final class OrderCompletionService {
    private final Plugin plugin;
    private final AOrderData orderData;
    private final AStatsData statsData;
    private final HistoryRecorder history;
    private final Treasury treasury;
    private final IntToDoubleFunction taxPercent;
    private final DoubleSupplier soloTaxPercent;
    private final Clock clock;
    private final Executor executor;

    /**
     * @param plugin     the plugin, firing the events
     * @param orderData  order repository
     * @param statsData  payout ledger
     * @param history    records the company's history entry
     * @param treasury   pays the workers and the collector
     * @param taxPercent     tax percentage of a company level
     * @param soloTaxPercent tax percentage of solo orders
     * @param clock          clock giving the completion time of the payouts
     * @param executor       runs the settlement off the main thread
     */
    public OrderCompletionService(
            Plugin plugin,
            AOrderData orderData,
            AStatsData statsData,
            HistoryRecorder history,
            Treasury treasury,
            IntToDoubleFunction taxPercent,
            DoubleSupplier soloTaxPercent,
            Clock clock,
            Executor executor) {
        this.plugin = plugin;
        this.orderData = orderData;
        this.statsData = statsData;
        this.history = history;
        this.treasury = treasury;
        this.taxPercent = taxPercent;
        this.soloTaxPercent = soloTaxPercent;
        this.clock = clock;
        this.executor = executor;
    }

    /**
     * The service of a running plugin: Vault pays the workers, the collector account gets fee and taxes, the company
     * treasury gets the payout shares, and the configuration gives the tax of a level.
     *
     * @param plugin        the plugin
     * @param orderData     order repository
     * @param statsData     payout ledger
     * @param history       records the company's history entry
     * @param economy       economy paying the workers
     * @param configuration configuration of the plugin
     * @param treasuries    the company treasuries
     * @return the service
     */
    public static OrderCompletionService create(
            Plugin plugin,
            AOrderData orderData,
            AStatsData statsData,
            HistoryRecorder history,
            Economy economy,
            Configuration configuration,
            TreasuryService treasuries) {
        var collector = new Collector(plugin, economy, configuration);
        Treasury treasury = new Treasury() {
            @Override
            public void pay(OfflinePlayer worker, double amount) {
                Threading.mainThread(plugin).execute(() -> economy.depositPlayer(worker, amount));
            }

            @Override
            public void collect(double amount) {
                collector.collect(amount);
            }

            @Override
            public int payoutShare(ISimpleCompany company) {
                return treasuries.effectiveShare(company.id()).join();
            }

            @Override
            public void keep(ISimpleCompany company, int order, long cents) {
                treasuries.keepShare(company, order, cents);
            }
        };
        return new OrderCompletionService(
                plugin,
                orderData,
                statsData,
                history,
                treasury,
                level -> configuration.companySettings().taxPercent(level),
                () -> configuration.orderSetting().solo().taxPercent(),
                Times.UTC,
                Threading.VIRTUAL);
    }

    /**
     * Marks a completely delivered order as delivered and settles it.
     *
     * @param order   claimed order whose lines are all delivered
     * @param company company which claimed the order
     * @return the completion, or empty when the order was not claimed, not complete or completed by another call
     */
    public CompletableFuture<Optional<Completion>> complete(SimpleOrder order, CompanyProfile company) {
        return orderData.submitOrderDelivered(order).asFuture().thenCompose(delivered -> delivered
                .map(fullOrder -> settle(fullOrder, company).thenApply(Optional::of))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
    }

    /**
     * Settles an order that is already marked delivered, such as one an admin marked delivered.
     *
     * @param order   delivered order with its deliveries
     * @param company company which delivered the order
     * @return the completion, once the ledger and the history entry were written
     */
    public CompletableFuture<Completion> settle(FullOrder order, CompanyProfile company) {
        return CompletableFuture.supplyAsync(() -> pay(order, company), executor)
                .thenCompose(completion -> record(completion, company).thenApply(ignored -> completion))
                .thenCompose(this::sinkIfSystem);
    }

    /**
     * Marks a completely delivered order a player worked on alone as delivered and settles it.
     *
     * @param order claimed solo order whose lines are all delivered
     * @return the completion, or empty when the order was not claimed, not complete or completed by another call
     */
    public CompletableFuture<Optional<Completion>> completeSolo(SimpleOrder order) {
        return orderData.submitOrderDelivered(order).asFuture().thenCompose(delivered -> delivered
                .map(fullOrder -> settleSolo(fullOrder).thenApply(Optional::of))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
    }

    /**
     * Settles an order a player worked on alone that is already marked delivered, such as one an admin marked
     * delivered.
     *
     * @param order delivered solo order with its deliveries
     * @return the completion, once the ledger was written
     */
    public CompletableFuture<Completion> settleSolo(FullOrder order) {
        return CompletableFuture.supplyAsync(() -> paySolo(order), executor)
                .thenCompose(completion -> recordPayouts(completion).thenApply(ignored -> completion))
                .thenCompose(this::sinkIfSystem);
    }

    /**
     * Completes a delivered system order at once: nobody receives its items, so they are removed, the order is marked
     * received and the owner's history entry is written for the server.
     */
    private CompletableFuture<Completion> sinkIfSystem(Completion completion) {
        var order = completion.order();
        if (!SystemOwner.is(order.owner())) return CompletableFuture.completedFuture(completion);
        return orderData
                .submitOrderReceive(order, List.of())
                .asFuture()
                .thenCompose(received -> Boolean.TRUE.equals(received)
                        ? history.ownerEntry(order, HistoryOutcome.RECEIVED).thenApply(ignored -> completion)
                        : CompletableFuture.completedFuture(completion))
                .exceptionally(err -> {
                    plugin.getLogger().log(Level.SEVERE, "Could not complete the system order " + order.id(), err);
                    return completion;
                });
    }

    private Completion pay(FullOrder order, CompanyProfile company) {
        plugin.getServer().getPluginManager().callEvent(new OrderDoneEvent(order, company));
        var percent = taxPercent.applyAsDouble(company.level());
        var sharePercent = treasury.payoutShare(company);
        var settled = payWorkers(
                order,
                company.id(),
                gross -> Payout.split(gross, percent, sharePercent),
                (player, payout) -> new OrderPaymentEvent(
                        order, company, player, payout.net(), payout.gross(), payout.tax(), payout.share()));
        if (settled.kept() > 0) treasury.keep(company, order.id(), settled.kept());
        return settled.completion();
    }

    private Completion paySolo(FullOrder order) {
        order.soloWorker()
                .map(worker -> plugin.getServer().getOfflinePlayer(worker))
                .ifPresent(worker ->
                        plugin.getServer().getPluginManager().callEvent(new SoloOrderDoneEvent(order, worker)));
        var percent = soloTaxPercent.getAsDouble();
        return payWorkers(
                        order,
                        0,
                        gross -> Payout.split(gross, percent),
                        (player, payout) ->
                                new SoloOrderPaymentEvent(order, player, payout.net(), payout.gross(), payout.tax()))
                .completion();
    }

    /**
     * Pays every worker their share of an order and hands the fee and the taxes to the collector. A payment whose
     * event was cancelled is recorded as not paid.
     *
     * @param order   delivered order
     * @param company id of the company that completed it, 0 for a solo order
     * @param split   splits the share of a worker
     * @param event   the cancellable payment event of a worker
     * @return the completion and the treasury shares kept, in cents
     */
    private Settled payWorkers(
            FullOrder order,
            int company,
            DoubleFunction<Payout> split,
            BiFunction<OfflinePlayer, Payout, ? extends Event> event) {
        var completed = LocalDateTime.now(clock);
        var items = itemsByWorker(order);
        List<PayoutRecord> ledger = new ArrayList<>();
        var collected = BigDecimal.valueOf(order.fee());
        var kept = 0L;
        for (var entry : order.payments(PaymentType.STACK).entrySet()) {
            var worker = entry.getKey();
            var player = plugin.getServer().getOfflinePlayer(worker);
            var payout = split.apply(entry.getValue());
            var payment = event.apply(player, payout);
            plugin.getServer().getPluginManager().callEvent(payment);
            var paid = !(payment instanceof Cancellable cancellable && cancellable.isCancelled());
            ledger.add(payoutRecord(order, company, worker, items.getOrDefault(worker, 0), payout, paid, completed));
            if (!paid) continue;
            treasury.pay(player, payout.net());
            collected = collected.add(BigDecimal.valueOf(payout.tax()));
            kept += payout.shareCents();
        }
        treasury.collect(collected.doubleValue());
        return new Settled(new Completion(order, List.copyOf(ledger)), kept);
    }

    private CompletableFuture<Boolean> recordPayouts(Completion completion) {
        return statsData.submitPayouts(completion.payouts()).exceptionally(err -> {
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            "Could not write the payouts of order "
                                    + completion.order().id(),
                            err);
            return false;
        });
    }

    private CompletableFuture<Void> record(Completion completion, CompanyProfile company) {
        var order = completion.order();
        var ledger = recordPayouts(completion);
        var entry = history.companyEntry(order, company.id(), HistoryOutcome.DELIVERED, completion.payouts())
                .exceptionally(err -> {
                    plugin.getLogger()
                            .log(Level.SEVERE, "Could not write the history entry of order " + order.id(), err);
                    return Optional.empty();
                });
        return CompletableFuture.allOf(ledger, entry);
    }

    /**
     * The items every worker delivered to an order.
     *
     * @param order order
     * @return delivered items by worker
     */
    static Map<UUID, Integer> itemsByWorker(FullOrder order) {
        Map<UUID, Integer> items = new HashMap<>();
        for (var content : order.contents()) {
            content.workerAmount().forEach((worker, amount) -> items.merge(worker, amount, Integer::sum));
        }
        return items;
    }

    /**
     * The ledger record of one payout.
     *
     * @param order     completed order
     * @param company   id of the company that completed it
     * @param worker    paid worker
     * @param items     items the worker delivered
     * @param payout    the worker's share split into tax, treasury share and net
     * @param paid      false when the payment was cancelled
     * @param completed when the order was completed
     * @return record
     */
    static PayoutRecord payoutRecord(
            FullOrder order,
            int company,
            UUID worker,
            int items,
            Payout payout,
            boolean paid,
            LocalDateTime completed) {
        return new PayoutRecord(
                order.id(),
                company,
                worker,
                items,
                payout.gross(),
                payout.tax(),
                payout.share(),
                payout.net(),
                paid,
                completed);
    }

    /**
     * Where the money of a completed order goes.
     */
    public interface Treasury {
        /**
         * Pays a worker their share after tax.
         *
         * @param worker paid worker
         * @param amount amount to pay
         */
        void pay(OfflinePlayer worker, double amount);

        /**
         * Hands the fee and the taxes of an order to the collector.
         *
         * @param amount amount collected
         */
        void collect(double amount);

        /**
         * The share of each payout the company keeps in its treasury, limited by the current cap. Called once per
         * completion, off the main thread.
         *
         * @param company company that completed the order
         * @return share in percent of the amount after tax
         */
        default int payoutShare(ISimpleCompany company) {
            return 0;
        }

        /**
         * Credits the treasury shares of an order to the company.
         *
         * @param company company that completed the order
         * @param order   id of the order
         * @param cents   sum of the shares of the paid workers, in cents
         */
        default void keep(ISimpleCompany company, int order, long cents) {}
    }

    /**
     * A settled order.
     *
     * @param order   the delivered order
     * @param payouts one record per worker, as written to the ledger
     */
    public record Completion(FullOrder order, List<PayoutRecord> payouts) {}

    /**
     * The payment of all workers of an order.
     *
     * @param completion the settled order
     * @param kept       treasury shares of the paid workers, in cents
     */
    private record Settled(Completion completion, long kept) {}
}
