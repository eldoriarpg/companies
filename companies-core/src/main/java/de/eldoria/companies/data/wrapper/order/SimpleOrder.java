/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.util.Texts;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public class SimpleOrder implements ISimpleOrder {
    private final int id;
    private final UUID owner;
    private final LocalDateTime created;
    private final int company;
    private final LocalDateTime claimed;
    private final OrderState state;
    private final double fee;
    private final int extensions;
    private final int deliveryBonus;
    private String name;

    public SimpleOrder(UUID owner, String name) {
        this(owner, name, 0.0);
    }

    /**
     * Creates an order which is not saved yet.
     *
     * @param owner owner of the order
     * @param name  name of the order
     * @param fee   fee the owner pays for placing the order
     */
    public SimpleOrder(UUID owner, String name, double fee) {
        id = -1;
        this.owner = owner;
        this.name = Texts.trimLeft(name, 32);
        created = null;
        company = -1;
        claimed = null;
        state = null;
        this.fee = fee;
        extensions = 0;
        deliveryBonus = 0;
    }

    /**
     * Creates a saved order.
     *
     * @param id            id of the order
     * @param owner         owner of the order
     * @param name          name of the order
     * @param created       when the order was placed
     * @param company       company working on the order, or 0 when none
     * @param claimed       last state change, which is when the order was accepted while it is claimed
     * @param state         state of the order
     * @param fee           fee the owner paid
     * @param extensions    how often the owner extended the purge timer
     * @param deliveryBonus extra delivery hours the accepting company got from its level
     */
    public SimpleOrder(
            int id,
            UUID owner,
            String name,
            LocalDateTime created,
            int company,
            LocalDateTime claimed,
            OrderState state,
            double fee,
            int extensions,
            int deliveryBonus) {
        this.id = id;
        this.owner = owner;
        this.name = Texts.trimLeft(name, 32);
        this.created = created;
        this.company = company;
        this.claimed = claimed;
        this.state = state;
        this.fee = fee;
        this.extensions = extensions;
        this.deliveryBonus = deliveryBonus;
    }

    public FullOrder toFullOrder(List<OrderContent> contents) {
        return new FullOrder(
                id, owner, name, created, company, claimed, state, fee, extensions, deliveryBonus, contents);
    }

    @Override
    public void name(String name) {
        this.name = name;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public UUID owner() {
        return owner;
    }

    @Override
    public String name() {
        return Texts.trimLeft(name, 32);
    }

    @Override
    public String fullName() {
        return String.format("%s - %s", id, name);
    }

    @Override
    public LocalDateTime created() {
        return created;
    }

    @Override
    public int company() {
        return company;
    }

    @Override
    public LocalDateTime claimed() {
        return claimed;
    }

    @Override
    public OrderState state() {
        return state;
    }

    @Override
    public double fee() {
        return fee;
    }

    /**
     * How often the owner extended the purge timer of this order.
     *
     * @return extensions used
     */
    public int extensions() {
        return extensions;
    }

    /**
     * How many extensions this order has left.
     *
     * @param maxExtensions extensions an order may have
     * @return extensions left, never negative
     */
    public int extensionsLeft(int maxExtensions) {
        return Math.max(0, maxExtensions - extensions);
    }

    /**
     * Extra delivery hours the accepting company got from its level.
     *
     * @return bonus hours, 0 while the order is not claimed
     */
    public int deliveryBonus() {
        return deliveryBonus;
    }

    /**
     * Hours the company has to deliver this order once it accepted it.
     *
     * @param configuration configuration providing the base delivery time
     * @return delivery hours including the level bonus
     */
    public int deliveryHours(Configuration configuration) {
        return configuration.companySettings().deliveryHours() + deliveryBonus;
    }

    public String runningOutTime(Configuration configuration) {
        var duration =
                Duration.between(LocalDateTime.now(), claimed().plus(deliveryHours(configuration), ChronoUnit.HOURS));
        return Texts.prettyDuration(duration);
    }
}
