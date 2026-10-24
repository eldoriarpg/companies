/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.IDeliveredStack;
import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * A player delivered items to an order they work on alone.
 * <p>
 * Fired once per delivery that recorded at least one item, after it was saved, usually off the main thread; the event
 * may be asynchronous. It fires only on the server where the player delivered. When the delivery completes the order,
 * {@link SoloOrderDoneEvent} follows. A delivery to an order of a company fires {@link OrderDeliveryEvent} instead.
 * <p>
 * Orders are anonymous: never show the {@link #owner() owner} to the worker.
 */
public class SoloOrderDeliveryEvent extends OrderEvent<IFullOrder> implements PlayerProvider<Player> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final List<? extends IDeliveredStack> deliveries;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param order      the order after the delivery
     * @param player     worker who delivered
     * @param deliveries items recorded by this delivery
     */
    public SoloOrderDeliveryEvent(IFullOrder order, Player player, List<? extends IDeliveredStack> deliveries) {
        super(order, !Bukkit.isPrimaryThread());
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
