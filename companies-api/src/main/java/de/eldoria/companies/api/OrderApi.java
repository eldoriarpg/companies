/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.CreateOrderResult;
import de.eldoria.companies.api.write.OrderRequest;
import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.components.order.IMaterialPrice;
import de.eldoria.companies.components.order.OrderState;
import org.bukkit.Material;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads and creates orders.
 * <p>
 * Every future completes off the main thread; see {@link CompaniesApi} for how to get back onto it. Orders are
 * anonymous towards players: never show the owner of an order to the company working on it, or the company to the
 * owner.
 */
public interface OrderApi {
    /**
     * The order with the given id.
     *
     * @param id id of the order
     * @return future of the order with its lines, empty when no order has this id
     */
    CompletableFuture<Optional<? extends IFullOrder>> byId(int id);

    /**
     * The orders a company has claimed, within a range of states.
     *
     * @param company id of the company
     * @param min     lowest state to include
     * @param max     highest state to include
     * @return future of the orders with their lines, empty when there are none
     */
    CompletableFuture<List<? extends IFullOrder>> byCompany(int company, OrderState min, OrderState max);

    /**
     * The orders a player has placed, within a range of states.
     *
     * @param owner id of the owner
     * @param min   lowest state to include
     * @param max   highest state to include
     * @return future of the orders with their lines, empty when there are none
     */
    CompletableFuture<List<? extends IFullOrder>> byOwner(UUID owner, OrderState min, OrderState max);

    /**
     * The prices other orders paid for a material.
     *
     * @param material material of the price
     * @return future of the price; all values are 0 when the material was never ordered
     */
    CompletableFuture<? extends IMaterialPrice> materialPrice(Material material);

    /**
     * Creates an order, the same way {@code /order create} does.
     * <p>
     * A {@link de.eldoria.companies.events.order.PreOrderCreateEvent} with the source
     * {@link de.eldoria.companies.components.order.OrderSource#API API} is fired on the main thread before anything is
     * charged; its listeners may change or cancel the order. After the order was saved an
     * {@link de.eldoria.companies.events.order.OrderCreatedEvent} follows.
     * <p>
     * See {@link OrderRequest#charge()} for who pays for the order and {@link OrderRequest#respectLimits()} for the
     * limits that apply. A rule the order breaks completes the future with a
     * {@link CreateOrderResult.Rejected rejection}, not exceptionally.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param request order to create
     * @return future of the created order or the reason it was not created
     */
    CompletableFuture<CreateOrderResult> create(OrderRequest request);
}
