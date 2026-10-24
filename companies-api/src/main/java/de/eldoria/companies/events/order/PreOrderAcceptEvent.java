/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.events.components.CancellableWithReason;
import de.eldoria.companies.events.components.CompanyProvider;
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
 * A member of a company is about to accept an order for the company.
 * <p>
 * Fired before the order is claimed, usually off the main thread; the event may be asynchronous. Cancelling it leaves
 * the order unclaimed and shows the {@link #cancelReason() reason} to the member. It fires only on the server where
 * the member accepts the order.
 * <p>
 * Orders are anonymous: never show the owner of the order to the member.
 * {@link OrderAcceptEvent} follows once the order is claimed. A player claiming an order alone fires
 * {@link PreSoloOrderAcceptEvent} instead.
 */
public class PreOrderAcceptEvent extends OrderEvent<IFullOrder>
        implements CompanyProvider<ICompanyProfile>, PlayerProvider<Player>, CancellableWithReason {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ICompanyProfile company;
    private final Player player;
    private boolean cancelled;

    @Nullable
    private Component reason;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param order   the unclaimed order
     * @param company company that will claim the order
     * @param player  member who accepts the order
     */
    public PreOrderAcceptEvent(IFullOrder order, ICompanyProfile company, Player player) {
        super(order, !Bukkit.isPrimaryThread());
        this.company = Objects.requireNonNull(company, "company");
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
     * The company that will claim the order.
     *
     * @return company
     */
    @Override
    public ICompanyProfile company() {
        return company;
    }

    /**
     * The member who accepts the order.
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
