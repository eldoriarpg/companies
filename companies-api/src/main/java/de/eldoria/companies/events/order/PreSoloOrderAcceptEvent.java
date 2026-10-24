/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.events.components.CancellableWithReason;
import de.eldoria.companies.events.components.PlayerProvider;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * A player is about to claim an order to work on it alone, without a company.
 * <p>
 * Fired before the order is claimed, usually off the main thread; the event may be asynchronous. Cancelling it leaves
 * the order unclaimed and shows the {@link #cancelReason() reason} to the player. It fires only on the server where
 * the player accepts the order. {@link SoloOrderAcceptEvent} follows once the order is claimed. A member accepting an
 * order for a company fires {@link PreOrderAcceptEvent} instead.
 * <p>
 * Orders are anonymous: never show the owner of the order to the player.
 */
public class PreSoloOrderAcceptEvent extends OrderEvent<IFullOrder>
        implements PlayerProvider<Player>, CancellableWithReason {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private boolean cancelled;

    @Nullable
    private Component reason;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param order  the unclaimed order
     * @param player player who will work on the order
     */
    public PreSoloOrderAcceptEvent(IFullOrder order, Player player) {
        super(order, !Bukkit.isPrimaryThread());
        this.player = Objects.requireNonNull(player, "player");
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
     * The player who will work on the order.
     *
     * @return player
     */
    @Override
    public Player player() {
        return player;
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
