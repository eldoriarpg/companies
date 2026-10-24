/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.components.order.OrderSource;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * An order was created and saved, by a player or through the API.
 * <p>
 * Fired after the order was saved, usually off the main thread; the event may be asynchronous. It fires only on the
 * server where the order was created.
 */
public class OrderCreatedEvent extends OrderEvent<IFullOrder> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer owner;
    private final OrderSource source;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param order  the saved order
     * @param owner  player who owns the order
     * @param source where the creation came from
     */
    public OrderCreatedEvent(IFullOrder order, OfflinePlayer owner, OrderSource source) {
        super(order, !Bukkit.isPrimaryThread());
        this.owner = Objects.requireNonNull(owner, "owner");
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public OfflinePlayer owner() {
        return owner;
    }

    /**
     * Where the creation came from.
     *
     * @return source of the order
     */
    public OrderSource source() {
        return source;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
