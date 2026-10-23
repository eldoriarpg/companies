/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ItemAmount;
import de.eldoria.companies.data.wrapper.order.ReturnedItems;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderReceivedEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.events.order.SoloOrderReceivedEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent.ReturnedStack;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.util.Threading;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ends orders, records every ending in the history of the side it concerns and fires the events of the ending.
 * <p>
 * Each method makes the conditional state change first and records the entry only when that change succeeded, so an
 * ending that lost a race leaves no entry. A history entry that could not be written is logged and does not undo the
 * ending. Abort and expiry load the deliveries of the order fresh, past the order cache, right before the change, so
 * the company's entry shows what was delivered before the items went back to the workers, and the item returns report
 * the returned items.
 * <p>
 * The events of an ending are fired on the executor, off the main thread, and only when the change succeeded: a
 * received order fires {@link OrderReceivedEvent}, or {@link SoloOrderReceivedEvent} when a player delivered it alone,
 * a cancelled or purged one {@link OrderRemovedEvent}, and items that did not fit or went back to workers
 * {@link ItemsReturnedEvent}.
 * <p>
 * Abort and expiry work the same for a player who claimed an order alone, except that no history entry is written,
 * since there is no company side.
 */
public final class OrderOutcomes {
    private final AOrderData orderData;
    private final HistoryRecorder history;
    private final ItemReturnService itemReturns;
    private final Consumer<Event> events;
    private final IntFunction<ISimpleCompany> companies;
    private final Logger logger;
    private final Executor executor;

    /**
     * @param orderData   order repository
     * @param history     writes the history entries
     * @param itemReturns reports the items returned to workers and owners
     * @param events      fires the events of the endings
     * @param companies   looks up the company that delivered a received order
     * @param logger      logs entries that could not be written
     * @param executor    loads orders and fires the events off the main thread
     */
    public OrderOutcomes(
            AOrderData orderData,
            HistoryRecorder history,
            ItemReturnService itemReturns,
            Consumer<Event> events,
            IntFunction<ISimpleCompany> companies,
            Logger logger,
            Executor executor) {
        this.orderData = orderData;
        this.history = history;
        this.itemReturns = itemReturns;
        this.events = events;
        this.companies = companies;
        this.logger = logger;
        this.executor = executor;
    }

    /**
     * The outcomes of a running plugin, firing the events through its plugin manager.
     *
     * @param plugin      the plugin
     * @param orderData   order repository
     * @param companyData company repository
     * @param history     writes the history entries
     * @param itemReturns reports returned items
     * @return the outcomes
     */
    public static OrderOutcomes create(
            Plugin plugin,
            AOrderData orderData,
            ACompanyData companyData,
            HistoryRecorder history,
            ItemReturnService itemReturns) {
        IntFunction<ISimpleCompany> companies =
                id -> Optional.ofNullable(companyData.retrieveCompanyById(id).join())
                        .flatMap(found -> found)
                        .<ISimpleCompany>map(found -> found)
                        .orElseGet(() -> ISimpleCompany.forId(id));
        return new OrderOutcomes(
                orderData,
                history,
                itemReturns,
                plugin.getServer().getPluginManager()::callEvent,
                companies,
                plugin.getLogger(),
                Threading.VIRTUAL);
    }

    /**
     * Marks a delivered order as received and records {@link HistoryOutcome#RECEIVED} for its owner. The pending
     * items are stored with the change and reported as {@link ItemsReturnedEvent.Reason#DELIVERY_OVERFLOW}.
     *
     * @param order   delivered order
     * @param pending items that did not fit into the owner's inventory
     * @return true when the order was delivered and is now received
     */
    public CompletableFuture<Boolean> receive(FullOrder order, List<ItemAmount> pending) {
        return ownerEnding(
                order,
                HistoryOutcome.RECEIVED,
                () -> orderData.submitOrderReceive(order, pending).asFuture(),
                () -> received(order, pending));
    }

    /**
     * Deletes an unclaimed order its owner cancelled and records {@link HistoryOutcome#CANCELLED} for the owner.
     *
     * @param order unclaimed order
     * @return true when the order was unclaimed and is now deleted
     */
    public CompletableFuture<Boolean> cancel(FullOrder order) {
        return ownerEnding(
                order,
                HistoryOutcome.CANCELLED,
                () -> orderData.submitOrderDeletion(order).asFuture(),
                () -> events.accept(new OrderRemovedEvent(order, OrderRemovedEvent.Reason.CANCELLED)));
    }

    /**
     * Removes an order nobody claimed in time and records {@link HistoryOutcome#PURGED} for its owner.
     *
     * @param order unclaimed order
     * @return true when the order was unclaimed and is now removed
     */
    public CompletableFuture<Boolean> purge(FullOrder order) {
        return ownerEnding(
                order,
                HistoryOutcome.PURGED,
                () -> orderData.submitOrderPurge(order).asFuture(),
                () -> events.accept(new OrderRemovedEvent(order, OrderRemovedEvent.Reason.PURGED)));
    }

    /**
     * Gives a claimed order back to the market for its company or its solo worker and records
     * {@link HistoryOutcome#ABORTED} for the company.
     *
     * @param order claimed order
     * @return the items returned to the workers, or empty when the order was not claimed
     */
    public CompletableFuture<Optional<ReturnedItems>> abort(SimpleOrder order) {
        return companyEnding(order, HistoryOutcome.ABORTED, ItemsReturnedEvent.Reason.ORDER_ABORTED);
    }

    /**
     * Releases a claimed order whose deadline passed and records {@link HistoryOutcome#EXPIRED} for the company, if a
     * company claimed it.
     *
     * @param order claimed order
     * @return the items returned to the workers, or empty when the order was not claimed
     */
    public CompletableFuture<Optional<ReturnedItems>> expire(SimpleOrder order) {
        return companyEnding(order, HistoryOutcome.EXPIRED, ItemsReturnedEvent.Reason.ORDER_EXPIRED);
    }

    private CompletableFuture<Boolean> ownerEnding(
            FullOrder order, HistoryOutcome outcome, Supplier<CompletableFuture<Boolean>> change, Runnable ended) {
        return change.get()
                .thenComposeAsync(
                        changed -> {
                            if (!Boolean.TRUE.equals(changed)) return CompletableFuture.completedFuture(false);
                            ended.run();
                            return logged(history.ownerEntry(order, outcome), order)
                                    .thenApply(ignored -> true);
                        },
                        executor);
    }

    private void received(FullOrder order, List<ItemAmount> pending) {
        var stacks = pending.stream()
                .filter(item -> item.amount() > 0)
                .map(item -> new ReturnedStack(item.stack(), item.amount()))
                .toList();
        itemReturns.returned(order.owner(), stacks, ItemsReturnedEvent.Reason.DELIVERY_OVERFLOW, order);
        var current = Optional.ofNullable(
                        orderData.retrieveOrderById(order.id()).join())
                .flatMap(found -> found)
                .map(orderData::toFullOrder)
                .orElse(order);
        var soloWorker = order.soloWorker();
        if (soloWorker.isPresent()) {
            events.accept(new SoloOrderReceivedEvent(current, Bukkit.getOfflinePlayer(soloWorker.get())));
            return;
        }
        events.accept(new OrderReceivedEvent(current, companies.apply(order.company())));
    }

    private CompletableFuture<Optional<ReturnedItems>> companyEnding(
            SimpleOrder order, HistoryOutcome outcome, ItemsReturnedEvent.Reason reason) {
        var company = order.company();
        return CompletableFuture.supplyAsync(() -> orderData.toFullOrder(order), executor)
                .thenCompose(fullOrder -> orderData
                        .submitUnclaimOrder(order)
                        .asFuture()
                        .thenCompose(returned -> {
                            if (returned == null || returned.isEmpty()) {
                                return CompletableFuture.completedFuture(Optional.<ReturnedItems>empty());
                            }
                            itemReturns.returned(order, returned.get(), reason);
                            if (order.isSolo()) return CompletableFuture.completedFuture(returned);
                            return logged(history.companyEntry(fullOrder, company, outcome, List.of()), order)
                                    .thenApply(ignored -> returned);
                        }));
    }

    private CompletableFuture<Optional<Long>> logged(CompletableFuture<Optional<Long>> entry, SimpleOrder order) {
        return entry.exceptionally(err -> {
            logger.log(Level.SEVERE, "Could not write the history entry of order " + order.id(), err);
            return Optional.empty();
        });
    }
}
