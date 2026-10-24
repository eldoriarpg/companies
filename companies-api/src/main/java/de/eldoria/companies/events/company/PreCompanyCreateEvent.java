/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.events.components.CancellableWithReason;
import de.eldoria.companies.events.components.PlayerProvider;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * A player is about to found a company.
 * <p>
 * Fired after the player confirmed and before the founding price is charged. The event may be asynchronous. Cancelling
 * it stops the founding, nothing is charged, and the player sees the {@link #cancelReason() reason}. It fires only on
 * the server where the player founds the company, and only while the player is online there: a company founded on the
 * web dashboard by a player who is not online skips it. {@link CompanyCreateEvent} follows once the company exists.
 */
public class PreCompanyCreateEvent extends Event implements PlayerProvider<Player>, CancellableWithReason {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String name;
    private final double price;
    private boolean cancelled;

    @Nullable
    private Component reason;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param player player founding the company
     * @param name   name of the new company
     * @param price  founding price the player will be charged
     */
    public PreCompanyCreateEvent(Player player, String name, double price) {
        super(!Bukkit.isPrimaryThread());
        this.player = Objects.requireNonNull(player, "player");
        this.name = Objects.requireNonNull(name, "name");
        this.price = price;
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
     * The player founding the company. They become its owner.
     *
     * @return founder
     */
    @Override
    public Player player() {
        return player;
    }

    /**
     * The name of the new company.
     *
     * @return name
     */
    public String name() {
        return name;
    }

    /**
     * The founding price the player will be charged.
     *
     * @return price, 0 when founding is free
     */
    public double price() {
        return price;
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
