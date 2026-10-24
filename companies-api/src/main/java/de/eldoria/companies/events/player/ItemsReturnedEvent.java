/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.player;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.components.PlayerProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Items were put into a player's returned items, which the player picks up with {@code /order collect}.
 * <p>
 * Fired after the items were stored, usually off the main thread; the event may be asynchronous. It fires only on the
 * server where the items were returned; expired orders are handled on the primary server of a network.
 * <p>
 * Orders are anonymous: when items return to a worker, never show the worker who owned the {@link #order() order}.
 */
public class ItemsReturnedEvent extends Event implements PlayerProvider<OfflinePlayer> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer player;
    private final List<ReturnedStack> stacks;
    private final Reason reason;

    @Nullable
    private final ISimpleOrder order;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param player player who gets the items
     * @param stacks the returned items
     * @param reason why the items were returned
     * @param order  order the items came from, or null when they do not belong to an order
     */
    public ItemsReturnedEvent(
            OfflinePlayer player, List<ReturnedStack> stacks, Reason reason, @Nullable ISimpleOrder order) {
        super(!Bukkit.isPrimaryThread());
        this.player = Objects.requireNonNull(player, "player");
        this.stacks = List.copyOf(stacks);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.order = order;
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
     * The player who gets the items.
     *
     * @return player
     */
    @Override
    public OfflinePlayer player() {
        return player;
    }

    /**
     * The returned items.
     *
     * @return unmodifiable list of the items
     */
    public List<ReturnedStack> stacks() {
        return stacks;
    }

    /**
     * Why the items were returned.
     *
     * @return reason
     */
    public Reason reason() {
        return reason;
    }

    /**
     * The order the items came from.
     *
     * @return the order, or empty for {@link Reason#ADMIN_GRANT}, {@link Reason#API} and items of an order that no
     *         longer exists
     */
    public Optional<ISimpleOrder> order() {
        return Optional.ofNullable(order);
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Why items were returned to a player.
     */
    public enum Reason {
        /**
         * The company or the player working on the order alone aborted it; the worker gets back what they
         * delivered.
         */
        ORDER_ABORTED,
        /**
         * The company or the player working on the order alone did not deliver it in time; the worker gets back
         * what they delivered.
         */
        ORDER_EXPIRED,
        /**
         * The company was disbanded while it worked on the order; the worker gets back what they delivered.
         */
        COMPANY_DISBANDED,
        /**
         * An admin cancelled the order or took it away from the company or the solo worker; the worker gets back
         * what they delivered.
         */
        ADMIN_RELEASE,
        /**
         * Items of a delivery could not be handed over: the owner's inventory could not hold all received items, or
         * the worker went offline before the surplus of a delivery was given back. The rest waits for them.
         */
        DELIVERY_OVERFLOW,
        /**
         * An admin granted the items with a command.
         */
        ADMIN_GRANT,
        /**
         * A plugin granted the items through {@link de.eldoria.companies.api.PlayerApi#grantItems}.
         */
        API
    }

    /**
     * Returned items of one kind.
     *
     * @param stack  the item with an amount of one; a copy is returned
     * @param amount how many of the item were returned, at least 1
     */
    public record ReturnedStack(ItemStack stack, int amount) {
        /**
         * Validates and copies the stack.
         *
         * @throws IllegalArgumentException when the amount is below 1
         */
        public ReturnedStack {
            Objects.requireNonNull(stack, "stack");
            if (amount < 1) throw new IllegalArgumentException("The amount must be at least 1, was " + amount);
            stack = stack.asOne();
        }

        /**
         * The item with an amount of one.
         *
         * @return a copy of the item
         */
        @Override
        public ItemStack stack() {
            return stack.clone();
        }
    }
}
