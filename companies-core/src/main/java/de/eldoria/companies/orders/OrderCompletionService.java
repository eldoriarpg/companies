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
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.stats.PayoutRecord;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.OrderPaymentEvent;
import de.eldoria.companies.events.order.SoloOrderDoneEvent;
import de.eldoria.companies.events.order.SoloOrderPaymentEvent;
import de.eldoria.companies.events.server.ServerEventPayoutEvent;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.services.events.EventEffects;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
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
 * Settling a delivered order fires {@link OrderDoneEvent}, pays every worker their share minus the tax of the company's
 * level and minus the company's treasury share, hands the fee and the taxes to the collector, credits the treasury
 * shares to the company in one transaction, writes one payout record per worker to the ledger and records the company's
 * history entry. A payment whose {@link OrderPaymentEvent} was cancelled is not paid and is written to the ledger as
 * not paid; it is neither taxed nor shared. Events are fired off the main thread, as they are asynchronous.
 * <p>
 * Server events active when the order completes and whose scope matches it, see {@link Boosts}, lower the tax and pay
 * extra on top. An order matches an item scope when any of its lines accepts one of its materials; a multiplier with an
 * item scope pays only on what a worker delivered of its materials, see {@link OrderMaterials}: the extra goes to the
 * worker alone, the treasury share is taken from the normal payout only. This holds for every order, the system orders
 * and the chunks of event offers included; the deliveries into contests are paid by the contest and get nothing from
 * server events. A worker paid an extra fires a {@link ServerEventPayoutEvent} before being paid.
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
    private final SystemReceipt systemReceipt;
    private final Boosts boosts;

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
     * @param systemReceipt  receives a delivered system order for the server
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
            Executor executor,
            SystemReceipt systemReceipt) {
        this(
                plugin,
                orderData,
                statsData,
                history,
                treasury,
                taxPercent,
                soloTaxPercent,
                clock,
                executor,
                systemReceipt,
                Boosts.NONE);
    }

    /**
     * @param plugin         the plugin, firing the events
     * @param orderData      order repository
     * @param statsData      payout ledger
     * @param history        records the company's history entry
     * @param treasury       pays the workers and the collector
     * @param taxPercent     tax percentage of a company level
     * @param soloTaxPercent tax percentage of solo orders
     * @param clock          clock giving the completion time of the payouts
     * @param executor       runs the settlement off the main thread
     * @param systemReceipt  receives a delivered system order for the server
     * @param boosts         the server events lowering the tax and paying extra
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
            Executor executor,
            SystemReceipt systemReceipt,
            Boosts boosts) {
        this.boosts = boosts;
        this.plugin = plugin;
        this.orderData = orderData;
        this.statsData = statsData;
        this.history = history;
        this.treasury = treasury;
        this.taxPercent = taxPercent;
        this.soloTaxPercent = soloTaxPercent;
        this.clock = clock;
        this.executor = executor;
        this.systemReceipt = systemReceipt;
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
     * @param outcomes      receives a delivered system order for the server
     * @param boosts        the server events lowering the tax and paying extra
     * @return the service
     */
    public static OrderCompletionService create(
            Plugin plugin,
            AOrderData orderData,
            AStatsData statsData,
            HistoryRecorder history,
            Economy economy,
            Configuration configuration,
            TreasuryService treasuries,
            OrderOutcomes outcomes,
            Boosts boosts) {
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
                Threading.VIRTUAL,
                order -> outcomes.receive(order, List.of()),
                boosts);
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
     * Completes a delivered system order at once: nobody receives its items, so they are removed and the order is
     * received for the server, as an owner receives an order, events and history entry included. One that could not be
     * received stays delivered and is received by the next expiry check.
     */
    private CompletableFuture<Completion> sinkIfSystem(Completion completion) {
        var order = completion.order();
        if (!SystemOwner.is(order.owner())) return CompletableFuture.completedFuture(completion);
        return systemReceipt.receive(order).handle((received, err) -> {
            if (err != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not complete the system order " + order.id(), err);
            } else if (!Boolean.TRUE.equals(received)) {
                plugin.getLogger()
                        .info("The system order " + order.id()
                                + " was not received here; it was received already or the next expiry check does it.");
            }
            return completion;
        });
    }

    private Completion pay(FullOrder order, CompanyProfile company) {
        plugin.getServer().getPluginManager().callEvent(new OrderDoneEvent(order, company));
        var completed = LocalDateTime.now(clock);
        var events = matching(order, OptionalInt.of(company.level()), completed);
        var percent = EventEffects.taxPercent(taxPercent.applyAsDouble(company.level()), events);
        var sharePercent = treasury.payoutShare(company);
        var settled = payWorkers(
                order,
                company.id(),
                completed,
                events,
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
        var completed = LocalDateTime.now(clock);
        var events = matching(order, OptionalInt.empty(), completed);
        var percent = EventEffects.taxPercent(soloTaxPercent.getAsDouble(), events);
        return payWorkers(
                        order,
                        0,
                        completed,
                        events,
                        gross -> Payout.split(gross, percent),
                        (player, payout) ->
                                new SoloOrderPaymentEvent(order, player, payout.net(), payout.gross(), payout.tax()))
                .completion();
    }

    private List<ServerEvent> matching(FullOrder order, OptionalInt level, LocalDateTime at) {
        try {
            return boosts.matching(order, level, at);
        } catch (RuntimeException e) {
            plugin.getLogger()
                    .log(Level.SEVERE, "Could not read the server events for order " + order.id() + "; none apply", e);
            return List.of();
        }
    }

    /**
     * Pays every worker their share of an order and hands the fee and the taxes to the collector. A payment whose
     * event was cancelled is recorded as not paid. The workers paid get what the reward multipliers of server events
     * pay on top, which is neither taxed nor shared with the treasury; when an event paid, the ledger is written
     * together with the events' spent money.
     *
     * @param order     delivered order
     * @param company   id of the company that completed it, 0 for a solo order
     * @param completed when the order was completed
     * @param events    the server events that apply to the order
     * @param split     splits the share of a worker
     * @param event     the cancellable payment event of a worker
     * @return the completion and the treasury shares kept, in cents
     */
    private Settled payWorkers(
            FullOrder order,
            int company,
            LocalDateTime completed,
            List<ServerEvent> events,
            DoubleFunction<Payout> split,
            BiFunction<OfflinePlayer, Payout, ? extends Event> event) {
        var items = itemsByWorker(order);
        List<PayoutRecord> ledger = new ArrayList<>();
        List<Share> shares = new ArrayList<>();
        Map<UUID, Payout> payouts = new LinkedHashMap<>();
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
            payouts.put(worker, payout);
            shares.add(new Share(worker, lines(order, worker)));
            collected = collected.add(BigDecimal.valueOf(payout.tax()));
            kept += payout.shareCents();
        }
        var funded = fund(order, events, shares, ledger);
        for (var entry : payouts.entrySet()) {
            var extra = funded.extras().getOrDefault(entry.getKey(), 0.0);
            var player = plugin.getServer().getOfflinePlayer(entry.getKey());
            if (extra > 0) {
                var paid = funded.paid().getOrDefault(entry.getKey(), List.of());
                plugin.getServer().getPluginManager().callEvent(new ServerEventPayoutEvent(order, player, extra, paid));
            }
            treasury.pay(player, entry.getValue().net() + extra);
        }
        treasury.collect(collected.doubleValue());
        return new Settled(new Completion(order, List.copyOf(funded.ledger()), funded.recorded()), kept);
    }

    private Funded fund(FullOrder order, List<ServerEvent> events, List<Share> shares, List<PayoutRecord> ledger) {
        if (events.isEmpty() || shares.isEmpty()) return Funded.none(ledger);
        try {
            return boosts.fund(events, shares, ledger);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not pay the server event extra of order " + order.id(), e);
            return Funded.none(ledger);
        }
    }

    /**
     * The gross a worker earns per line of an order and material the worker delivered to it, see
     * {@link OrderMaterials#delivered}.
     *
     * @param order  the order
     * @param worker the worker
     * @return the parts the worker delivered
     */
    static List<EventEffects.Line> lines(FullOrder order, UUID worker) {
        return OrderMaterials.delivered(order, worker).stream()
                .map(part -> new EventEffects.Line(part.material(), part.gross()))
                .toList();
    }

    private CompletableFuture<Boolean> recordPayouts(Completion completion) {
        if (completion.recorded()) return CompletableFuture.completedFuture(true);
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
     * @param order    the delivered order
     * @param payouts  one record per worker, as written to the ledger
     * @param recorded whether the ledger is written already, together with the money server events paid
     */
    public record Completion(FullOrder order, List<PayoutRecord> payouts, boolean recorded) {
        /**
         * A settled order whose ledger is still to be written.
         *
         * @param order   the delivered order
         * @param payouts one record per worker
         */
        public Completion(FullOrder order, List<PayoutRecord> payouts) {
            this(order, payouts, false);
        }
    }

    /**
     * The server events of a completed order: which apply, and paying their extra.
     */
    public interface Boosts {
        /**
         * No server events.
         */
        Boosts NONE = new Boosts() {
            @Override
            public List<ServerEvent> matching(FullOrder order, OptionalInt level, LocalDateTime at) {
                return List.of();
            }

            @Override
            public Funded fund(List<ServerEvent> events, List<Share> shares, List<PayoutRecord> ledger) {
                return Funded.none(ledger);
            }
        };

        /**
         * The events that apply to an order. Called off the main thread.
         *
         * @param order the completed order
         * @param level the level of the company, empty for an order worked on alone
         * @param at    when the order was completed
         * @return the events, the soonest start first
         */
        List<ServerEvent> matching(FullOrder order, OptionalInt level, LocalDateTime at);

        /**
         * Pays the extra of the reward multipliers among the events: takes the money from the events' funding, adds
         * it to their spent money and writes the ledger in one transaction. Called off the main thread, before the
         * workers are paid.
         *
         * @param events the events that apply to the order
         * @param shares the gross per line of every worker who is paid
         * @param ledger one record per worker, without extra
         * @return the extra of every worker and the ledger with it
         */
        Funded fund(List<ServerEvent> events, List<Share> shares, List<PayoutRecord> ledger);
    }

    /**
     * What a paid worker earns per line of an order.
     *
     * @param worker the worker
     * @param lines  the gross per line, by the line's material
     */
    public record Share(UUID worker, List<EventEffects.Line> lines) {
        /**
         * @param worker the worker
         * @param lines  the gross per line
         */
        public Share {
            lines = List.copyOf(lines);
        }
    }

    /**
     * The extra server events paid for an order.
     *
     * @param extras   the extra of every worker, by worker; missing for a worker without extra
     * @param ledger   one record per worker, with the extra
     * @param recorded whether the ledger is written already
     * @param paid     what each event paid every worker, by worker; only events that paid
     */
    public record Funded(
            Map<UUID, Double> extras,
            List<PayoutRecord> ledger,
            boolean recorded,
            Map<UUID, List<ServerEventPayoutEvent.Paid>> paid) {
        /**
         * @param extras   the extra of every worker
         * @param ledger   one record per worker
         * @param recorded whether the ledger is written already
         * @param paid     what each event paid every worker
         */
        public Funded {
            extras = Map.copyOf(extras);
            ledger = List.copyOf(ledger);
            paid = Map.copyOf(paid);
        }

        /**
         * @param ledger one record per worker
         * @return no extra, the ledger still to be written
         */
        public static Funded none(List<PayoutRecord> ledger) {
            return new Funded(Map.of(), ledger, false, Map.of());
        }
    }

    /**
     * Receives a delivered system order for the server.
     */
    @FunctionalInterface
    public interface SystemReceipt {
        /**
         * @param order the delivered system order
         * @return true when it was delivered and is now received
         */
        CompletableFuture<Boolean> receive(FullOrder order);
    }

    /**
     * The payment of all workers of an order.
     *
     * @param completion the settled order
     * @param kept       treasury shares of the paid workers, in cents
     */
    private record Settled(Completion completion, long kept) {}
}
