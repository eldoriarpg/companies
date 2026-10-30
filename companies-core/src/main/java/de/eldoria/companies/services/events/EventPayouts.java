/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.wrapper.event.EventKind;
import de.eldoria.companies.data.wrapper.event.EventPayout;
import de.eldoria.companies.data.wrapper.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.stats.PayoutRecord;
import de.eldoria.companies.orders.EconomyAccounts;
import de.eldoria.companies.orders.OrderCompletionService;
import de.eldoria.companies.orders.OrderCompletionService.Funded;
import de.eldoria.companies.orders.OrderCompletionService.Share;
import de.eldoria.companies.orders.OrderMaterials;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies the server events to the payouts of completed orders.
 * <p>
 * The events that apply come from the {@link ActiveEvents} of this node: an order matches an item scope when one of its
 * lines accepts one of the scope's materials, see {@link OrderMaterials#accepted(FullOrder)}. A multiplier pays on the
 * parts of a worker's gross whose delivered material its scope covers, see {@link OrderMaterials#delivered}. The extra
 * of the reward multipliers is paid from the events as they are in the database right now, never from the cache: per
 * event, at most what is left of its budget and, when it is funded from an account, at most the account's balance,
 * shared by every event of that account. The workers of an order are taken one after another, each with what the ones
 * before left, and every event's part is rounded down to the cent.
 * <p>
 * Funding an order runs in this order: the balances are read and the money of the accounts is withdrawn on the main
 * thread; then the events' spent money and the ledger are written in one transaction, which adds an event's money only
 * while it is active and its budget covers it (see {@link AServerEventData#pay}). When an account cannot pay or the
 * transaction is refused, because another node spent the same budget meanwhile, the withdrawn money goes back and the
 * order is funded again from what is left, up to {@value #ATTEMPTS} times; after that the workers get no extra. When a
 * withdrawal or the transaction fails with an error, what was withdrawn goes back too and the workers get no extra.
 * Minted money is not withdrawn from anywhere. An event that could pay less than its part pays what it has left and is
 * {@link ServerEventService#deplete depleted}.
 * <p>
 * Methods block on the database and wait for the main thread, at most {@link #MAIN_THREAD_TIMEOUT} per call, see
 * {@link Funding#onMainThread}; call them off the main thread. Once {@link #stop()} ran, while the plugin stops,
 * nothing is withdrawn anymore and no extra is paid.
 */
public final class EventPayouts implements OrderCompletionService.Boosts {
    /**
     * How often an order is funded at most when other nodes spend the same events meanwhile.
     */
    public static final int ATTEMPTS = 4;

    /**
     * How long reading a balance or withdrawing waits for the main thread at most.
     */
    public static final Duration MAIN_THREAD_TIMEOUT = Duration.ofSeconds(10);

    private final ActiveEvents active;
    private final AServerEventData data;
    private final ServerEventService service;
    private final Funding funding;
    private final DoubleSupplier maxMultiplier;
    private final Logger logger;
    private volatile boolean stopped;

    /**
     * @param active        the events of this node
     * @param data          event repository, read fresh when paying and writing the ledger
     * @param service       depletes events that ran out of money
     * @param funding       the economy accounts of events funded from an account
     * @param maxMultiplier the cap of all multipliers together, {@code events.maxMultiplier}
     * @param logger        reports orders that could not be funded
     */
    public EventPayouts(
            ActiveEvents active,
            AServerEventData data,
            ServerEventService service,
            Funding funding,
            DoubleSupplier maxMultiplier,
            Logger logger) {
        this.active = active;
        this.data = data;
        this.service = service;
        this.funding = funding;
        this.maxMultiplier = maxMultiplier;
        this.logger = logger;
    }

    /**
     * Pays no more extra from here on, since the plugin stops: nothing is withdrawn anymore and the workers of orders
     * completed now get no extra.
     */
    public void stop() {
        stopped = true;
    }

    @Override
    public List<ServerEvent> matching(FullOrder order, OptionalInt level, LocalDateTime at) {
        return active.matching(at, OrderMaterials.accepted(order), level);
    }

    @Override
    public Funded fund(List<ServerEvent> events, List<Share> shares, List<PayoutRecord> ledger) {
        var multipliers = events.stream()
                .filter(event -> event.kind() == EventKind.REWARD_MULTIPLIER)
                .toList();
        if (multipliers.isEmpty() || shares.isEmpty()) return Funded.none(ledger);
        for (var attempt = 0; attempt < ATTEMPTS; attempt++) {
            if (stopped) return Funded.none(ledger);
            var fresh = multipliers.stream()
                    .map(event -> data.event(event.id()))
                    .flatMap(Optional::stream)
                    .filter(event -> event.state() == EventState.ACTIVE)
                    .toList();
            if (fresh.isEmpty()) return Funded.none(ledger);
            var plan = plan(fresh, shares);
            if (!(plan.total() > 0)) {
                deplete(plan.exhausted());
                return Funded.none(ledger);
            }
            Map<String, Double> withdrawn = new LinkedHashMap<>();
            var payouts = payouts(ledger, plan);
            boolean paid;
            if (stopped) return Funded.none(ledger);
            try {
                if (!withdraw(plan.accounts(), withdrawn)) {
                    giveBack(withdrawn);
                    continue;
                }
                paid = data.pay(plan.spend(), payouts);
            } catch (RuntimeException e) {
                giveBack(withdrawn);
                logger.log(
                        Level.SEVERE, "Could not pay the server event extra of an order; the workers get no extra", e);
                return Funded.none(ledger);
            }
            if (paid) {
                deplete(plan.exhausted());
                return new Funded(
                        plan.extras(), payouts.stream().map(EventPayout::record).toList(), true);
            }
            giveBack(withdrawn);
        }
        logger.warning("Could not pay the server event extra of an order after " + ATTEMPTS
                + " attempts, since other servers kept spending the same events; the workers get no extra.");
        return Funded.none(ledger);
    }

    private Plan plan(List<ServerEvent> events, List<Share> shares) {
        Map<Integer, Double> left = new HashMap<>();
        Map<String, Double> balances = new HashMap<>();
        for (var event : events) {
            left.put(event.id(), event.remaining().orElse(Double.POSITIVE_INFINITY));
            var account = account(event);
            if (account != null && !balances.containsKey(account)) {
                balances.put(account, Math.max(0, funding.balance(account)));
            }
        }
        var cap = maxMultiplier.getAsDouble();
        Map<UUID, Double> extras = new LinkedHashMap<>();
        Map<UUID, Map<Integer, Double>> byWorker = new LinkedHashMap<>();
        Map<Integer, Double> spend = new LinkedHashMap<>();
        Map<String, Double> accounts = new LinkedHashMap<>();
        Set<Integer> exhausted = new LinkedHashSet<>();
        for (var share : shares) {
            var extra = EventEffects.extra(
                    share.lines(),
                    events,
                    cap,
                    event -> {
                        var account = account(event);
                        var limit = left.getOrDefault(event.id(), 0.0);
                        return account == null ? limit : Math.min(limit, balances.getOrDefault(account, 0.0));
                    },
                    (event, pays) -> {
                        var account = account(event);
                        if (account != null && pays > 0) balances.merge(account, -pays, Double::sum);
                    });
            exhausted.addAll(extra.exhausted());
            Map<Integer, Double> parts = new LinkedHashMap<>();
            var sum = BigDecimal.ZERO;
            for (var part : extra.byEvent().entrySet()) {
                var cents = BigDecimal.valueOf(part.getValue()).setScale(2, RoundingMode.FLOOR);
                if (cents.signum() <= 0) continue;
                var amount = cents.doubleValue();
                var event = byId(events, part.getKey());
                parts.put(part.getKey(), amount);
                sum = sum.add(cents);
                left.merge(part.getKey(), -amount, Double::sum);
                spend.merge(part.getKey(), amount, EventPayouts::sum);
                var account = event == null ? null : account(event);
                if (account != null) accounts.merge(account, amount, EventPayouts::sum);
            }
            if (sum.signum() > 0) {
                extras.put(share.worker(), sum.doubleValue());
                byWorker.put(share.worker(), parts);
            }
        }
        var total = spend.values().stream().mapToDouble(Double::doubleValue).sum();
        return new Plan(extras, byWorker, spend, accounts, exhausted, total);
    }

    private static double sum(double left, double right) {
        return BigDecimal.valueOf(left).add(BigDecimal.valueOf(right)).doubleValue();
    }

    private static @Nullable ServerEvent byId(List<ServerEvent> events, int id) {
        return events.stream().filter(event -> event.id() == id).findFirst().orElse(null);
    }

    private static @Nullable String account(ServerEvent event) {
        return event.funding() == MoneySource.SourceType.ACCOUNT ? event.account() : null;
    }

    /**
     * Withdraws what every account pays, noting each withdrawal as soon as it is done, so the caller gives back what
     * was taken also when a later withdrawal throws.
     *
     * @param accounts  what each account pays
     * @param withdrawn receives what was withdrawn
     * @return true when every account paid
     */
    private boolean withdraw(Map<String, Double> accounts, Map<String, Double> withdrawn) {
        for (var entry : accounts.entrySet()) {
            if (!(entry.getValue() > 0)) continue;
            if (!funding.withdraw(entry.getKey(), entry.getValue())) return false;
            withdrawn.put(entry.getKey(), entry.getValue());
        }
        return true;
    }

    private void giveBack(Map<String, Double> withdrawn) {
        for (var entry : withdrawn.entrySet()) {
            try {
                funding.deposit(entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                logger.log(
                        Level.SEVERE,
                        "Could not give %s back to the account %s".formatted(entry.getValue(), entry.getKey()),
                        e);
            }
        }
    }

    private static List<EventPayout> payouts(List<PayoutRecord> ledger, Plan plan) {
        List<EventPayout> payouts = new ArrayList<>();
        for (var record : ledger) {
            var extra = record.paid() ? plan.extras().getOrDefault(record.worker(), 0.0) : 0.0;
            var parts = extra > 0 ? plan.byWorker().getOrDefault(record.worker(), Map.of()) : Map.<Integer, Double>of();
            payouts.add(new EventPayout(record.withEventExtra(extra), parts));
        }
        return payouts;
    }

    private void deplete(Set<Integer> exhausted) {
        for (var id : exhausted) {
            try {
                service.deplete(id);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not deplete the server event " + id, e);
            }
        }
    }

    /**
     * How the extra of an order is paid.
     *
     * @param extras    the extra of every worker, by worker
     * @param byWorker  what each event pays for every worker
     * @param spend     what each event pays in all
     * @param accounts  what each account pays in all
     * @param exhausted the events that could pay less than their part
     * @param total     the extra in all
     */
    private record Plan(
            Map<UUID, Double> extras,
            Map<UUID, Map<Integer, Double>> byWorker,
            Map<Integer, Double> spend,
            Map<String, Double> accounts,
            Set<Integer> exhausted,
            double total) {}

    /**
     * The economy accounts paying the events funded from an account. Calls block until the main thread ran them.
     */
    public interface Funding {
        /**
         * @param account the account
         * @return what it holds
         */
        double balance(String account);

        /**
         * @param account the account
         * @param amount  amount to take
         * @return true when the account held the amount and it was taken
         */
        boolean withdraw(String account, double amount);

        /**
         * @param account the account
         * @param amount  amount to give back
         */
        void deposit(String account, double amount);

        /**
         * The accounts of the economy, used on the main thread, see {@link #onMainThread}.
         *
         * @param accounts   the economy accounts
         * @param mainThread runs the economy calls
         * @param running    whether the plugin is enabled and the server is not stopping
         * @param logger     reports deposits the economy refused and late withdrawals
         * @return the funding
         */
        static Funding economy(EconomyAccounts accounts, Executor mainThread, BooleanSupplier running, Logger logger) {
            var direct = new Funding() {
                @Override
                public double balance(String account) {
                    return accounts.balance(account);
                }

                @Override
                public boolean withdraw(String account, double amount) {
                    return accounts.withdraw(account, amount);
                }

                @Override
                public void deposit(String account, double amount) {
                    var response = accounts.deposit(account, amount);
                    if (!response.transactionSuccess()) {
                        logger.warning("Could not give %s back to the account %s: %s"
                                .formatted(amount, account, response.errorMessage));
                    }
                }
            };
            return onMainThread(direct, mainThread, running, MAIN_THREAD_TIMEOUT, logger);
        }

        /**
         * Runs a funding on the main thread.
         * <p>
         * Reading a balance and withdrawing wait at most {@code timeout} for the main thread, such as while the server
         * stops, and fail with an exception after it, so the order is paid without extra and nothing withdrawn is
         * kept. A withdrawal that the main thread still runs after the timeout gives its money back at once. Money
         * given back is deposited on the main thread while the plugin runs, and on the calling thread when the plugin
         * is not enabled anymore, the server stops or the main thread takes no more tasks, so it is never lost with
         * the tasks the server drops.
         *
         * @param direct     the funding, called on the main thread
         * @param mainThread runs the economy calls
         * @param running    whether the plugin is enabled and the server is not stopping
         * @param timeout    how long reading a balance or withdrawing waits for the main thread
         * @param logger     reports late withdrawals
         * @return the funding
         */
        static Funding onMainThread(
                Funding direct, Executor mainThread, BooleanSupplier running, Duration timeout, Logger logger) {
            return new Funding() {
                @Override
                public double balance(String account) {
                    return await(CompletableFuture.supplyAsync(() -> direct.balance(account), mainThread), timeout);
                }

                @Override
                public boolean withdraw(String account, double amount) {
                    var withdrawal = CompletableFuture.supplyAsync(() -> direct.withdraw(account, amount), mainThread);
                    try {
                        return await(withdrawal, timeout);
                    } catch (IllegalStateException e) {
                        withdrawal.thenAccept(taken -> {
                            if (!Boolean.TRUE.equals(taken)) return;
                            logger.warning("A withdrawal of %s from %s ran after its timeout and is given back"
                                    .formatted(amount, account));
                            direct.deposit(account, amount);
                        });
                        throw e;
                    }
                }

                @Override
                public void deposit(String account, double amount) {
                    if (!(amount > 0)) return;
                    if (!running.getAsBoolean()) {
                        direct.deposit(account, amount);
                        return;
                    }
                    try {
                        mainThread.execute(() -> direct.deposit(account, amount));
                    } catch (RuntimeException e) {
                        logger.log(Level.WARNING, "The main thread takes no tasks, depositing on this thread", e);
                        direct.deposit(account, amount);
                    }
                }
            };
        }

        private static <T> T await(CompletableFuture<T> call, Duration timeout) {
            try {
                return call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new IllegalStateException("The main thread did not answer within " + timeout, e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("The economy failed", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the main thread", e);
            }
        }
    }
}
