/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.CreateOrderResult;
import de.eldoria.companies.api.write.OrderLineRequest;
import de.eldoria.companies.api.write.OrderRequest;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.components.order.IMaterialPrice;
import de.eldoria.companies.components.order.IOrderDraft;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.SystemOwner;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Reads orders from the repository and creates them through the order creation, like {@code /order create}.
 */
final class OrderApiImpl implements OrderApi {
    private final AOrderData orderData;
    private final WriteAccess writes;
    private final Function<UUID, OfflinePlayer> players;
    private final Function<CommandFailure, Component> messages;
    private final Executor executor;

    OrderApiImpl(
            AOrderData orderData,
            WriteAccess writes,
            Function<UUID, OfflinePlayer> players,
            Function<CommandFailure, Component> messages,
            Executor executor) {
        this.orderData = orderData;
        this.writes = writes;
        this.players = players;
        this.messages = messages;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Optional<? extends IFullOrder>> byId(int id) {
        return CompletableFuture.<Optional<? extends IFullOrder>>supplyAsync(
                () -> orderData.retrieveOrderById(id).asFuture().join().map(orderData::toFullOrder), executor);
    }

    @Override
    public CompletableFuture<List<? extends IFullOrder>> byCompany(int company, OrderState min, OrderState max) {
        return CompletableFuture.<List<? extends IFullOrder>>supplyAsync(
                () -> orderData.toFullOrders(orderData
                        .retrieveOrdersByCompany(ISimpleCompany.forId(company), min, max)
                        .asFuture()
                        .join()),
                executor);
    }

    @Override
    public CompletableFuture<List<? extends IFullOrder>> bySoloWorker(UUID worker, OrderState min, OrderState max) {
        return CompletableFuture.<List<? extends IFullOrder>>supplyAsync(
                () -> orderData.toFullOrders(orderData
                        .retrieveOrdersBySoloWorker(worker, min, max)
                        .asFuture()
                        .join()),
                executor);
    }

    @Override
    public CompletableFuture<List<? extends IFullOrder>> byOwner(UUID owner, OrderState min, OrderState max) {
        return CompletableFuture.<List<? extends IFullOrder>>supplyAsync(
                () -> orderData.toFullOrders(orderData
                        .retrieveOrdersByPlayer(players.apply(owner), min, max)
                        .asFuture()
                        .join()),
                executor);
    }

    @Override
    public CompletableFuture<? extends IMaterialPrice> materialPrice(Material material) {
        return CompletableFuture.supplyAsync(
                () -> orderData
                        .retrieveMaterialPrice(material.name())
                        .asFuture()
                        .join(),
                executor);
    }

    @Override
    public CompletableFuture<CreateOrderResult> create(OrderRequest request) {
        if (SystemOwner.is(request.owner())) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("The owner id " + request.owner() + " is reserved for the server."));
        }
        return writes.run(services -> services.orders()
                        .create(
                                players.apply(request.owner()),
                                draft(request),
                                OrderSource.API,
                                request.charge(),
                                request.respectLimits()))
                .handleAsync(this::result, executor);
    }

    private CreateOrderResult result(OrderCreationService.Outcome outcome, Throwable err) {
        if (err != null) throw unsaved(err);
        return switch (outcome) {
            case OrderCreationService.Created created -> new CreateOrderResult.Created(created.order());
            case OrderCreationService.Rejected rejected ->
                new CreateOrderResult.Rejected(rejected.reason(), messages.apply(rejected.failure()));
        };
    }

    private static CompletionException unsaved(Throwable err) {
        var cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
        if (cause instanceof CommandFailure) {
            return new CompletionException(new IllegalStateException("The order could not be saved", cause));
        }
        return new CompletionException(cause);
    }

    private static IOrderDraft draft(OrderRequest request) {
        var draft = IOrderDraft.of(request.name());
        for (OrderLineRequest line : request.lines()) {
            if (line.exact()) {
                draft.addLine(line.template(), line.amount(), line.unitPrice());
            } else {
                draft.addLine(line.template().getType(), line.amount(), line.unitPrice());
            }
        }
        return draft;
    }
}
