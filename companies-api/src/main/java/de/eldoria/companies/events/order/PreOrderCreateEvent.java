/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.IOrderDraft;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.events.components.CancellableWithReason;
import de.eldoria.companies.events.components.OwnerProvider;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * An order is about to be created, by a player or through the API.
 * <p>
 * Fired on the main thread before the owner is charged. Listeners may change the {@link #draft()}: rename it, change,
 * remove or add lines. Once all listeners ran, the server's limits are checked again and the fee is computed from the
 * adjusted price. A draft that breaks the limits or has no lines left is not created and nothing is charged.
 * <p>
 * Cancelling the event stops the creation and nothing is charged. A player sees the {@link #cancelReason() reason};
 * an API caller gets it in its {@link de.eldoria.companies.api.write.CreateOrderResult.Rejected rejection}.
 * <p>
 * The event fires only on the server where the order is created.
 */
public class PreOrderCreateEvent extends Event implements OwnerProvider, CancellableWithReason {
    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer owner;
    private final IOrderDraft draft;
    private final OrderSource source;
    private boolean cancelled;

    @Nullable
    private Component reason;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param owner  player who will own the order
     * @param draft  the order as it will be created
     * @param source where the creation came from
     */
    public PreOrderCreateEvent(OfflinePlayer owner, IOrderDraft draft, OrderSource source) {
        super(!Bukkit.isPrimaryThread());
        this.owner = Objects.requireNonNull(owner, "owner");
        this.draft = Objects.requireNonNull(draft, "draft");
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

    /**
     * The player who will own the order. They may be offline when the order is created through the API,
     * placed on the dashboard or posted by a repeating order.
     *
     * @return owner of the order
     */
    @Override
    public OfflinePlayer owner() {
        return owner;
    }

    /**
     * The order as it will be created. Changes to it apply to the created order.
     *
     * @return the draft
     */
    public IOrderDraft draft() {
        return draft;
    }

    /**
     * Where the creation came from.
     *
     * @return source of the order
     */
    public OrderSource source() {
        return source;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
        if (!cancel) reason = null;
    }

    @Override
    public void cancel(Component reason) {
        this.reason = Objects.requireNonNull(reason, "reason");
        cancelled = true;
    }

    @Override
    public Optional<Component> cancelReason() {
        return cancelled ? Optional.ofNullable(reason) : Optional.empty();
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
