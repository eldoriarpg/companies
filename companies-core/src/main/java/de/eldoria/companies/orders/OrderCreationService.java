/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.api.write.RejectReason;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.IDraftLine;
import de.eldoria.companies.components.order.IOrderDraft;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.events.order.OrderCreatedEvent;
import de.eldoria.companies.events.order.PreOrderCreateEvent;
import de.eldoria.companies.util.Money;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates orders, for players finishing a draft and for plugins using the API.
 * <p>
 * The draft is checked against the order settings first; the limits of lines, items and active orders only apply when
 * requested. A {@link PreOrderCreateEvent} is fired on the main thread next. Its listeners may change or cancel the
 * draft, so it is checked again afterwards and the fee is computed from the adjusted price. A draft a listener made
 * invalid is rejected with {@link RejectReason#INVALID}. Only then the owner is charged, on the main thread, and the
 * order is saved; a failed save refunds the owner and completes the future exceptionally with a
 * {@link CommandFailure}. An {@link OrderCreatedEvent} follows the save, off the main thread.
 * <p>
 * Uncharged orders are funded by the server: nothing is withdrawn and the order stores a fee of 0.
 */
public final class OrderCreationService {
    private final AOrderData orderData;
    private final Supplier<OrderSettings> settings;
    private final OrderLimits limits;
    private final Function<ItemStack, Optional<OrderDraftService.RequestedItem>> requests;
    private final Wallet wallet;
    private final Consumer<Event> events;
    private final Executor mainThread;
    private final Logger logger;

    /**
     * @param orderData  order repository
     * @param settings   current order settings
     * @param limits     limits of active orders per player
     * @param requests   turns an item into what an order line requests, empty when it can not be ordered
     * @param wallet     charges and refunds owners
     * @param events     fires events
     * @param mainThread runs tasks on the main thread
     * @param logger     logs failed saves
     */
    public OrderCreationService(
            AOrderData orderData,
            Supplier<OrderSettings> settings,
            OrderLimits limits,
            Function<ItemStack, Optional<OrderDraftService.RequestedItem>> requests,
            Wallet wallet,
            Consumer<Event> events,
            Executor mainThread,
            Logger logger) {
        this.orderData = orderData;
        this.settings = settings;
        this.limits = limits;
        this.requests = requests;
        this.wallet = wallet;
        this.events = events;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    /**
     * Creates an order from a draft.
     *
     * @param owner         player who owns the order
     * @param draft         the order to create; listeners of the pre-event may change it
     * @param source        where the creation came from
     * @param charge        true to charge the owner price and fee, false to fund the order by the server
     * @param respectLimits true to apply the limits of lines, items and active orders
     * @return the created order or why it was not created; exceptionally with a {@link CommandFailure} when the
     *         order could not be saved
     */
    public CompletableFuture<Outcome> create(
            OfflinePlayer owner, IOrderDraft draft, OrderSource source, boolean charge, boolean respectLimits) {
        return CompletableFuture.supplyAsync(() -> prepare(owner, draft, source, respectLimits), mainThread)
                .thenCompose(order -> checkActive(owner, order, respectLimits))
                .thenApplyAsync(order -> charge(owner, order, charge), mainThread)
                .thenCompose(order -> save(owner, order, source))
                .handle(OrderCreationService::outcome);
    }

    private static Outcome outcome(@Nullable Outcome outcome, @Nullable Throwable err) {
        if (err == null) return Objects.requireNonNull(outcome);
        var cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
        if (cause instanceof Rejection rejection) return rejection.rejected();
        throw err instanceof CompletionException completion ? completion : new CompletionException(err);
    }

    private PendingOrder prepare(OfflinePlayer owner, IOrderDraft draft, OrderSource source, boolean respectLimits) {
        violation(draft, respectLimits).ifPresent(OrderCreationService::reject);
        var event = new PreOrderCreateEvent(owner, draft, source);
        events.accept(event);
        if (event.isCancelled()) {
            var failure = event.cancelReason()
                    .map(reason -> CommandFailure.of("error.cancelledByIntegration", reason))
                    .orElseGet(() -> CommandFailure.of("error.cancelledByIntegration"));
            reject(new Rejected(RejectReason.CANCELLED, failure));
        }
        if (violation(draft, respectLimits).isPresent()) {
            reject(new Rejected(RejectReason.INVALID, CommandFailure.of("error.integrationDraftInvalid")));
        }
        return build(owner, draft, respectLimits);
    }

    private Optional<Rejected> violation(IOrderDraft draft, boolean respectLimits) {
        if (draft.lines().isEmpty()) return rejected(RejectReason.INVALID, "order.create.error.empty");
        if (draft.name().length() >= OrderDraftService.MAX_NAME_LENGTH) {
            return Optional.of(new Rejected(
                    RejectReason.INVALID,
                    CommandFailure.of(
                            "error.invalidLength",
                            Replacement.create("max", String.valueOf(OrderDraftService.MAX_NAME_LENGTH)))));
        }
        var broken = draft.lines().stream()
                .anyMatch(line -> line.amount() < 1 || line.unitPrice() < 0 || !Double.isFinite(line.unitPrice()));
        if (broken) return rejected(RejectReason.INVALID, "error.integrationDraftInvalid");
        if (!respectLimits) return Optional.empty();
        var current = settings.get();
        if (draft.lines().size() > current.maxMaterials()) {
            return rejected(RejectReason.TOO_MANY_LINES, "order.create.error.materialLimit");
        }
        if (draft.amount() > current.maxItems()) {
            return rejected(RejectReason.TOO_MANY_ITEMS, "order.create.error.itemLimit");
        }
        return Optional.empty();
    }

    private PendingOrder build(OfflinePlayer owner, IOrderDraft draft, boolean respectLimits) {
        var builder = new OrderBuilder(owner.getUniqueId(), draft.name());
        for (var line : draft.lines()) {
            var requested = requested(line)
                    .orElseThrow(() -> new Rejection(
                            new Rejected(RejectReason.INVALID, CommandFailure.of("order.draft.error.notOrderable"))));
            builder.add(requested.template(), requested.exact(), line.amount(), line.unitPrice());
        }
        var limit = respectLimits ? limits.limit(owner) : Integer.MAX_VALUE;
        return new PendingOrder(builder, limit);
    }

    private Optional<OrderDraftService.RequestedItem> requested(IDraftLine line) {
        if (!line.exact()) return Optional.of(new OrderDraftService.RequestedItem(line.template(), false));
        return requests.apply(line.template());
    }

    private CompletableFuture<PendingOrder> checkActive(
            OfflinePlayer owner, PendingOrder order, boolean respectLimits) {
        if (!respectLimits) return CompletableFuture.completedFuture(order);
        return orderData.retrievePlayerOrderCount(owner).asFuture().thenApply(active -> {
            if (active >= order.limit()) {
                reject(new Rejected(RejectReason.LIMIT_REACHED, CommandFailure.of("order.create.error.limitReached")));
            }
            return order;
        });
    }

    private PendingOrder charge(OfflinePlayer owner, PendingOrder order, boolean charge) {
        var price = order.builder().price();
        var fee = charge ? settings.get().fee().of(price) : 0.0;
        var charged = charge ? price + fee : 0.0;
        if (charged > 0 && !wallet.withdraw(owner, charged)) {
            reject(new Rejected(
                    RejectReason.INSUFFICIENT_FUNDS,
                    CommandFailure.of(
                            "error.insufficientCurrency",
                            Replacement.create("currency", wallet.currencyName()),
                            Replacement.create("amount", wallet.format(charged)))));
        }
        return order.charged(fee, charged);
    }

    private CompletableFuture<Outcome> save(OfflinePlayer owner, PendingOrder order, OrderSource source) {
        var price = order.builder().price();
        return orderData
                .submitOrder(owner, order.builder().build(order.fee()))
                .asFuture()
                .exceptionally(err -> {
                    logger.log(Level.SEVERE, "Could not save order", err);
                    return Optional.empty();
                })
                .thenCompose(saved -> {
                    if (saved.isEmpty()) return refund(owner, order.charged());
                    return CompletableFuture.completedFuture(created(owner, saved.get(), source, price));
                });
    }

    private Outcome created(OfflinePlayer owner, FullOrder order, OrderSource source, double price) {
        events.accept(new OrderCreatedEvent(order, owner, source));
        return new Created(order, price, order.fee());
    }

    private CompletableFuture<Outcome> refund(OfflinePlayer owner, double charged) {
        return CompletableFuture.supplyAsync(
                () -> {
                    if (charged > 0) wallet.deposit(owner, charged);
                    throw CommandFailure.of("error.saveFailed");
                },
                mainThread);
    }

    private static Optional<Rejected> rejected(RejectReason reason, String localeKey) {
        return Optional.of(new Rejected(reason, CommandFailure.of(localeKey)));
    }

    private static void reject(Rejected rejected) {
        throw new Rejection(rejected);
    }

    /**
     * Moves money between the owners of orders and the server.
     */
    public interface Wallet {
        /**
         * A wallet backed by the Vault economy.
         *
         * @param economy the economy
         * @return the wallet
         */
        static Wallet of(Economy economy) {
            return new Wallet() {
                @Override
                public boolean withdraw(OfflinePlayer player, double amount) {
                    return economy.has(player, amount)
                            && economy.withdrawPlayer(player, amount).transactionSuccess();
                }

                @Override
                public void deposit(OfflinePlayer player, double amount) {
                    economy.depositPlayer(player, amount);
                }

                @Override
                public String format(double amount) {
                    return economy.format(amount);
                }

                @Override
                public String currencyName() {
                    return Money.currencyName(economy);
                }
            };
        }

        /**
         * Takes money from a player. Called on the main thread.
         *
         * @param player player to charge
         * @param amount amount to take
         * @return false when the player can not afford it; nothing was taken then
         */
        boolean withdraw(OfflinePlayer player, double amount);

        /**
         * Gives money to a player. Called on the main thread.
         *
         * @param player player to pay
         * @param amount amount to give
         */
        void deposit(OfflinePlayer player, double amount);

        /**
         * @param amount amount of money
         * @return the amount as the economy shows it
         */
        String format(double amount);

        /**
         * @return name of the currency
         */
        String currencyName();
    }

    /**
     * What came of creating an order.
     */
    public sealed interface Outcome permits Created, Rejected {}

    /**
     * The order was created.
     *
     * @param order the saved order
     * @param price price of the order
     * @param fee   fee the owner paid on top, 0 for orders funded by the server
     */
    public record Created(FullOrder order, double price, double fee) implements Outcome {}

    /**
     * The order was not created and nothing was charged.
     *
     * @param reason  why it was not created
     * @param failure the error the owner sees
     */
    public record Rejected(RejectReason reason, CommandFailure failure) implements Outcome {}

    private record PendingOrder(OrderBuilder builder, int limit, double fee, double charged) {
        private PendingOrder(OrderBuilder builder, int limit) {
            this(builder, limit, 0.0, 0.0);
        }

        private PendingOrder charged(double fee, double charged) {
            return new PendingOrder(builder, limit, fee, charged);
        }
    }

    /**
     * Ends the creation with a rejection; turned into the {@link Rejected} outcome at the end of the chain.
     */
    private static final class Rejection extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient Rejected rejected;

        private Rejection(Rejected rejected) {
            super(rejected.reason().name(), null, false, false);
            this.rejected = rejected;
        }

        private Rejected rejected() {
            return rejected;
        }
    }
}
