/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.order;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.events.components.CompanyProvider;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The owner received the items of a delivered order.
 * <p>
 * Fired after the order changed to {@link de.eldoria.companies.components.order.OrderState#RECEIVED}, usually off the
 * main thread; the event may be asynchronous. It fires only on the server where the owner received the order. An
 * order a player delivered alone fires {@link SoloOrderReceivedEvent} instead.
 * <p>
 * Orders are anonymous: never show the {@link #company() company} to the owner.
 */
public class OrderReceivedEvent extends OrderEvent<IFullOrder> implements CompanyProvider<ISimpleCompany> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ISimpleCompany company;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param order   the received order
     * @param company company that delivered the order
     */
    public OrderReceivedEvent(IFullOrder order, ISimpleCompany company) {
        super(order, !Bukkit.isPrimaryThread());
        this.company = Objects.requireNonNull(company, "company");
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
     * The company that delivered the order. When the company was disbanded since, only its {@link ISimpleCompany#id()
     * id} is known, as with {@link ISimpleCompany#forId(int)}.
     *
     * @return company
     */
    @Override
    public ISimpleCompany company() {
        return company;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
