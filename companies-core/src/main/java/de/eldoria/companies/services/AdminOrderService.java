/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.orders.Collector;
import de.eldoria.companies.orders.HistoryRecorder;
import de.eldoria.companies.orders.OrderCompletionService;
import de.eldoria.companies.orders.PaymentType;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.Threading;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The order actions of admins: taking an order away from its company, deleting it with or without a refund, marking it
 * delivered and giving it more time.
 * <p>
 * Every action loads the order fresh, checks its state and then makes a conditional change in the database, so an
 * action that lost a race against a player or another server changes nothing and is refused. Only after the change
 * succeeded is money moved, the history written and the affected players notified. Players are never told which admin
 * acted, and owner and company still do not learn about each other.
 * <p>
 * Money follows the fair settlement:
 * <ul>
 *     <li>deleting with a refund returns price and fee to the owner; deleting without one returns nothing and the fee
 *     goes to the collector;</li>
 *     <li>marking a partly delivered order delivered pays the workers for what they delivered, as a normal completion
 *     does, and refunds the owner the price share of the missing items.</li>
 * </ul>
 * Deposits are made on the main thread. Released and deleted orders fire {@link ItemsReturnedEvent} with
 * {@link ItemsReturnedEvent.Reason#ADMIN_RELEASE} for the workers' items, and a deletion fires {@link OrderRemovedEvent}
 * with {@link OrderRemovedEvent.Reason#ADMIN}. Marking an order delivered fires the events of a normal completion.
 * Deleting a copy of a repeating order stops the repeating order.
 */
public final class AdminOrderService {
    private final AOrderData orderData;
    private final IntFunction<Optional<CompanyProfile>> companies;
    private final OrderCompletionService completion;
    private final HistoryRecorder history;
    private final Funds funds;
    private final Notices notices;
    private final ItemReturnService itemReturns;
    private final Consumer<Event> events;
    private final Consumer<SimpleOrder> copyRemoved;
    private final Logger logger;
    private final Executor executor;

    /**
     * @param orderData  order repository
     * @param companies  loads a company with its members by id
     * @param completion settles orders marked delivered
     * @param history    writes the history entries
     * @param funds      refunds owners and pays the collector
     * @param notices     tells the affected players
     * @param itemReturns reports the items returned to workers and tells them
     * @param events      fires the events of the actions
     * @param copyRemoved stops the repeating order of a deleted copy
     * @param logger      logs history entries that could not be written
     * @param executor    runs the actions off the main thread
     */
    public AdminOrderService(
            AOrderData orderData,
            IntFunction<Optional<CompanyProfile>> companies,
            OrderCompletionService completion,
            HistoryRecorder history,
            Funds funds,
            Notices notices,
            ItemReturnService itemReturns,
            Consumer<Event> events,
            Consumer<SimpleOrder> copyRemoved,
            Logger logger,
            Executor executor) {
        this.orderData = orderData;
        this.companies = companies;
        this.completion = completion;
        this.history = history;
        this.funds = funds;
        this.notices = notices;
        this.itemReturns = itemReturns;
        this.events = events;
        this.copyRemoved = copyRemoved;
        this.logger = logger;
        this.executor = executor;
    }

    /**
     * The service of a running plugin: Vault refunds the owners, the collector account gets kept fees, and the
     * notifications go to the inbox.
     *
     * @param plugin        the plugin
     * @param orderData     order repository
     * @param companyData   company repository
     * @param completion    settles orders marked delivered
     * @param history       writes the history entries
     * @param economy       economy refunding the owners
     * @param configuration configuration of the plugin
     * @param notifications sends the notifications
     * @param itemReturns   reports returned items and tells online workers about them
     * @param series        stops the repeating order of a deleted copy
     * @return the service
     */
    public static AdminOrderService create(
            Plugin plugin,
            AOrderData orderData,
            ACompanyData companyData,
            OrderCompletionService completion,
            HistoryRecorder history,
            Economy economy,
            Configuration configuration,
            NotificationService notifications,
            ItemReturnService itemReturns,
            SeriesService series) {
        var collector = new Collector(plugin, economy, configuration);
        var mainThread = Threading.mainThread(plugin);
        Funds funds = new Funds() {
            @Override
            public void refund(UUID owner, double amount) {
                mainThread.execute(
                        () -> economy.depositPlayer(plugin.getServer().getOfflinePlayer(owner), amount));
            }

            @Override
            public void collect(double amount) {
                collector.collect(amount);
            }
        };
        IntFunction<Optional<CompanyProfile>> companies = id -> Optional.ofNullable(
                        companyData.retrieveCompanyById(id).join())
                .flatMap(found -> found)
                .flatMap(found -> Optional.ofNullable(
                                companyData.retrieveCompanyProfile(found).join())
                        .flatMap(profile -> profile));
        return new AdminOrderService(
                orderData,
                companies,
                completion,
                history,
                funds,
                new InboxNotices(notifications, economy::format),
                itemReturns,
                plugin.getServer().getPluginManager()::callEvent,
                order -> series.onCopyRemovedByAdmin(order).exceptionally(err -> {
                    plugin.getLogger().log(Level.SEVERE, "Could not stop the repeating order of " + order.id(), err);
                    return false;
                }),
                plugin.getLogger(),
                Threading.VIRTUAL);
    }

    /**
     * Takes a claimed order away from its company. The delivered items go back to the workers, the company gets no
     * penalty, and the order is open for every company again.
     *
     * @param id id of the order
     * @return the released order, or why it was refused
     */
    public CompletableFuture<Result<Unclaimed>> unclaim(int id) {
        return CompletableFuture.supplyAsync(() -> unclaimNow(id), executor);
    }

    /**
     * Deletes an open or claimed order. The items delivered to a claimed order go back to the workers.
     *
     * @param id     id of the order
     * @param refund true to return price and fee to the owner, false to keep the price and collect the fee
     * @return the deleted order, or why it was refused
     */
    public CompletableFuture<Result<Cancelled>> cancel(int id, boolean refund) {
        return CompletableFuture.supplyAsync(() -> cancelNow(id, refund), executor);
    }

    /**
     * Marks a claimed order delivered, however much was delivered, and settles it: the workers are paid for what they
     * delivered and the owner gets the price share of the missing items back.
     *
     * @param id id of the order
     * @return the settled order with the refund, or why it was refused
     */
    public CompletableFuture<Result<Delivered>> deliver(int id) {
        return CompletableFuture.supplyAsync(() -> deliverNow(id), executor);
    }

    /**
     * Gives an order more time: a claimed order more delivery time, an open order more time before it is removed.
     * The owner's extensions are not used up.
     *
     * @param id    id of the order
     * @param hours hours to add
     * @return the extended order, or why it was refused
     */
    public CompletableFuture<Result<Extended>> extend(int id, int hours) {
        return CompletableFuture.supplyAsync(() -> extendNow(id, hours), executor);
    }

    /**
     * What a deletion moves.
     *
     * @param order  order as it was before the deletion
     * @param refund true to refund the owner
     * @return price and fee for the owner on a refund, otherwise the fee for the collector
     */
    public static CancelMoney cancelMoney(FullOrder order, boolean refund) {
        if (refund) {
            var total = BigDecimal.valueOf(order.price()).add(BigDecimal.valueOf(order.fee()));
            return new CancelMoney(total.doubleValue(), 0);
        }
        return new CancelMoney(0, order.fee());
    }

    /**
     * The price share of the items missing from an order, which is what the workers are not paid for.
     *
     * @param order delivered order with its deliveries
     * @return the price minus the workers' shares, rounded to cents and never negative
     */
    public static double undeliveredRefund(FullOrder order) {
        var paid = order.payments(PaymentType.STACK).values().stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var refund = BigDecimal.valueOf(order.price()).subtract(paid).setScale(2, RoundingMode.HALF_UP);
        return refund.max(BigDecimal.ZERO).doubleValue();
    }

    private Result<Unclaimed> unclaimNow(int id) {
        var found = order(id);
        if (found.isEmpty()) return Result.refused(Refusal.UNKNOWN_ORDER);
        var order = found.get();
        if (order.state() != OrderState.CLAIMED) return Result.refused(Refusal.NOT_CLAIMED);
        var snapshot = orderData.toFullOrder(order);
        var returned = orderData.submitUnclaimOrder(order).join();
        if (returned == null || returned.isEmpty()) return Result.refused(Refusal.NOT_CLAIMED);
        record(() -> history.companyEntry(snapshot, order.company(), HistoryOutcome.UNCLAIMED_BY_ADMIN, List.of()), id);
        companies.apply(order.company()).ifPresent(company -> notices.companyUnclaimed(company, order));
        notices.ownerReopened(order);
        itemReturns.returned(order, returned.get(), ItemsReturnedEvent.Reason.ADMIN_RELEASE);
        return Result.done(new Unclaimed(snapshot, returned.get().workers()));
    }

    private Result<Cancelled> cancelNow(int id, boolean refund) {
        var found = order(id);
        if (found.isEmpty()) return Result.refused(Refusal.UNKNOWN_ORDER);
        var order = found.get();
        if (!isOpen(order)) return Result.refused(Refusal.NOT_OPEN);
        var snapshot = orderData.toFullOrder(order);
        var returned = orderData.submitAdminDeletion(order).join();
        if (returned == null || returned.isEmpty()) return Result.refused(Refusal.NOT_OPEN);
        var money = cancelMoney(snapshot, refund);
        if (money.refund() > 0) funds.refund(order.owner(), money.refund());
        if (money.collected() > 0) funds.collect(money.collected());
        var claimed = order.state() == OrderState.CLAIMED;
        record(() -> history.ownerEntry(snapshot, HistoryOutcome.CANCELLED_BY_ADMIN), id);
        if (claimed) {
            record(
                    () -> history.companyEntry(snapshot, order.company(), HistoryOutcome.CANCELLED_BY_ADMIN, List.of()),
                    id);
            companies.apply(order.company()).ifPresent(company -> notices.companyCancelled(company, order));
        }
        notices.ownerCancelled(order, money.refund());
        itemReturns.returned(order, returned.get(), ItemsReturnedEvent.Reason.ADMIN_RELEASE);
        events.accept(new OrderRemovedEvent(order, OrderRemovedEvent.Reason.ADMIN));
        if (order.seriesId().isPresent()) copyRemoved.accept(order);
        return Result.done(new Cancelled(snapshot, claimed, returned.get().workers(), money));
    }

    private Result<Delivered> deliverNow(int id) {
        var found = order(id);
        if (found.isEmpty()) return Result.refused(Refusal.UNKNOWN_ORDER);
        var order = found.get();
        if (order.state() != OrderState.CLAIMED) return Result.refused(Refusal.NOT_CLAIMED);
        var company = companies.apply(order.company());
        if (company.isEmpty()) return Result.refused(Refusal.UNKNOWN_COMPANY);
        var delivered = orderData.submitForcedDelivery(order).join();
        if (delivered == null || delivered.isEmpty()) return Result.refused(Refusal.NOT_CLAIMED);
        var fullOrder = delivered.get();
        var refund = undeliveredRefund(fullOrder);
        var settled = completion.settle(fullOrder, company.get()).join();
        if (refund > 0) {
            funds.refund(order.owner(), refund);
            notices.ownerRefunded(order, refund);
        }
        return Result.done(new Delivered(settled, refund));
    }

    private Result<Extended> extendNow(int id, int hours) {
        var found = order(id);
        if (found.isEmpty()) return Result.refused(Refusal.UNKNOWN_ORDER);
        var order = found.get();
        var extension =
                switch (order.state()) {
                    case CLAIMED -> orderData.submitClaimExtension(order, hours);
                    case UNCLAIMED -> orderData.submitUnclaimedExtension(order, hours);
                    default -> null;
                };
        if (extension == null || !Boolean.TRUE.equals(extension.join())) return Result.refused(Refusal.NOT_OPEN);
        return Result.done(new Extended(order, hours));
    }

    private Optional<SimpleOrder> order(int id) {
        var found = orderData.retrieveOrderById(id).join();
        return found == null ? Optional.empty() : found;
    }

    private static boolean isOpen(SimpleOrder order) {
        return order.state() == OrderState.UNCLAIMED || order.state() == OrderState.CLAIMED;
    }

    private void record(Supplier<CompletableFuture<Optional<Long>>> entry, int order) {
        try {
            entry.get().join();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not write the history entry of order " + order, e);
        }
    }

    /**
     * Why an action was refused.
     */
    public enum Refusal {
        /**
         * No order has this id.
         */
        UNKNOWN_ORDER("error.unknownOrder"),
        /**
         * The action needs a claimed order.
         */
        NOT_CLAIMED("companyadmin.order.error.notClaimed"),
        /**
         * The action needs an open or a claimed order.
         */
        NOT_OPEN("companyadmin.order.error.notOpen"),
        /**
         * The company that claimed the order does not exist any more.
         */
        UNKNOWN_COMPANY("error.unknownCompany");

        private final String key;

        Refusal(String key) {
            this.key = key;
        }

        /**
         * @return locale key of the error
         */
        public String key() {
            return key;
        }
    }

    /**
     * The outcome of an action.
     *
     * @param <T> what a performed action returns
     */
    public sealed interface Result<T> {
        /**
         * @param value what the action returns
         * @param <T>   type of the value
         * @return a performed action
         */
        static <T> Result<T> done(T value) {
            return new Done<>(value);
        }

        /**
         * @param refusal why the action was refused
         * @param <T>     type of the value of a performed action
         * @return a refused action
         */
        static <T> Result<T> refused(Refusal refusal) {
            return new Refused<>(refusal);
        }

        /**
         * A performed action.
         *
         * @param value what the action returns
         * @param <T>   type of the value
         */
        record Done<T>(T value) implements Result<T> {}

        /**
         * A refused action, which changed nothing.
         *
         * @param refusal why it was refused
         * @param <T>     type of the value of a performed action
         */
        record Refused<T>(Refusal refusal) implements Result<T> {}
    }

    /**
     * A released order.
     *
     * @param order   the order as it was before the release, with its deliveries
     * @param workers workers who got items returned
     */
    public record Unclaimed(FullOrder order, Set<UUID> workers) {
        public Unclaimed {
            workers = Set.copyOf(workers);
        }
    }

    /**
     * A deleted order.
     *
     * @param order       the order as it was before the deletion
     * @param wasClaimed  whether a company worked on it
     * @param workers     workers who got items returned
     * @param money       what was refunded and collected
     */
    public record Cancelled(FullOrder order, boolean wasClaimed, Set<UUID> workers, CancelMoney money) {
        public Cancelled {
            workers = Set.copyOf(workers);
        }
    }

    /**
     * What a deletion refunds and collects.
     *
     * @param refund    amount refunded to the owner
     * @param collected amount handed to the collector
     */
    public record CancelMoney(double refund, double collected) {}

    /**
     * An order marked delivered and settled.
     *
     * @param completion the settlement with the payouts
     * @param refund     price share of the missing items, refunded to the owner
     */
    public record Delivered(OrderCompletionService.Completion completion, double refund) {}

    /**
     * An extended order.
     *
     * @param order order as it was before the extension
     * @param hours hours added
     */
    public record Extended(SimpleOrder order, int hours) {}

    /**
     * Where the money of an admin action goes.
     */
    public interface Funds {
        /**
         * Gives money back to an owner.
         *
         * @param owner  owner of the order
         * @param amount amount to refund
         */
        void refund(UUID owner, double amount);

        /**
         * Hands a kept fee to the collector.
         *
         * @param amount amount collected
         */
        void collect(double amount);
    }

    /**
     * Tells the players affected by an admin action, never naming the admin, the owner to the company or the company
     * to the owner.
     */
    public interface Notices {
        /**
         * @param order order which is open again
         */
        void ownerReopened(SimpleOrder order);

        /**
         * @param company company the order was taken from
         * @param order   the order
         */
        void companyUnclaimed(CompanyProfile company, SimpleOrder order);

        /**
         * @param order  deleted order
         * @param refund amount refunded, zero when nothing was refunded
         */
        void ownerCancelled(SimpleOrder order, double refund);

        /**
         * @param company company which worked on the deleted order
         * @param order   the order
         */
        void companyCancelled(CompanyProfile company, SimpleOrder order);

        /**
         * @param order  order marked delivered
         * @param refund price share of the missing items
         */
        void ownerRefunded(SimpleOrder order, double refund);
    }

    /**
     * Notices through the inbox.
     *
     * @param notifications sends the notifications
     * @param money         formats amounts of money
     */
    record InboxNotices(NotificationService notifications, DoubleFunction<String> money) implements Notices {
        @Override
        public void ownerReopened(SimpleOrder order) {
            notifications.notifyOrderOpen(order);
        }

        @Override
        public void companyUnclaimed(CompanyProfile company, SimpleOrder order) {
            notifications.sendCompany(
                    company, NotificationType.ORDER_UNCLAIMED_BY_ADMIN, NotificationService.orderArgs(order));
        }

        @Override
        public void ownerCancelled(SimpleOrder order, double refund) {
            if (refund > 0) {
                notifications.send(
                        order.owner(), NotificationType.MY_ORDER_CANCELLED_BY_ADMIN, withAmount(order, refund));
                return;
            }
            notifications.send(
                    order.owner(),
                    NotificationType.MY_ORDER_CANCELLED_BY_ADMIN_KEPT,
                    NotificationService.orderArgs(order));
        }

        @Override
        public void companyCancelled(CompanyProfile company, SimpleOrder order) {
            notifications.sendCompany(
                    company, NotificationType.ORDER_CANCELLED_BY_ADMIN, NotificationService.orderArgs(order));
        }

        @Override
        public void ownerRefunded(SimpleOrder order, double refund) {
            notifications.send(order.owner(), NotificationType.MY_ORDER_PARTLY_REFUNDED, withAmount(order, refund));
        }

        private NotificationArgs withAmount(SimpleOrder order, double amount) {
            return NotificationService.orderArgs(order).money("amount", money.apply(amount));
        }
    }
}
