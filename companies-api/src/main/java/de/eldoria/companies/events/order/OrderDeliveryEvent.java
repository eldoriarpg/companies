/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.IDeliveredStack;
import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.events.components.CompanyProvider;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * A worker delivered items to an order.
 * <p>
 * Fired once per delivery that recorded at least one item, after it was saved, usually off the main thread; the event
 * may be asynchronous. It fires only on the server where the worker delivered. When the delivery completes the order,
 * {@link OrderDoneEvent} follows.
 * <p>
 * Orders are anonymous: never show the {@link #owner() owner} to the worker or the other members of the company.
 */
public class OrderDeliveryEvent extends OrderEvent<IFullOrder>
        implements CompanyProvider<ICompanyProfile>, PlayerProvider<Player> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ICompanyProfile company;
    private final Player player;
    private final List<? extends IDeliveredStack> deliveries;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param order      the order after the delivery
     * @param company    company working on the order
     * @param player     worker who delivered
     * @param deliveries items recorded by this delivery
     */
    public OrderDeliveryEvent(
            IFullOrder order, ICompanyProfile company, Player player, List<? extends IDeliveredStack> deliveries) {
        super(order, !Bukkit.isPrimaryThread());
        this.company = Objects.requireNonNull(company, "company");
        this.player = Objects.requireNonNull(player, "player");
        this.deliveries = List.copyOf(deliveries);
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * The company working on the order.
     *
     * @return company
     */
    @Override
    public ICompanyProfile company() {
        return company;
    }

    /**
     * The worker who delivered.
     *
     * @return player
     */
    @Override
    public Player player() {
        return player;
    }

    /**
     * The items recorded by this delivery only.
     *
     * @return unmodifiable list of the delivered stacks, not empty
     */
    public List<? extends IDeliveredStack> deliveries() {
        return deliveries;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
