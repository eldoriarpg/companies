/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.events;

import de.eldoria.companies.data.wrapper.offer.OfferKind;

/**
 * A goal or a contest of a template of {@code events.yml}, created with each of its events and run with it. The fields
 * are those of {@code /companyadmin offers goal} and {@code contest}: what it orders as a {@code material} or
 * {@code criteria}, the items in all and the price per item, for a goal the chunk size and the chunks waiting at once,
 * for a contest the prizes, and where its money comes from. Its times are those of the event.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class OfferTemplate {
    private OfferKind kind = OfferKind.GOAL;
    private String name = "";
    private String money = "minted";
    private String material = "";
    private String criteria = "";
    private int amount = 0;
    private double price = 0.0;
    private int chunk = 64;
    private int open = 3;
    private String prizes = "";
    private double budget = 0.0;

    public OfferTemplate() {}

    /**
     * @return goal or contest
     */
    public OfferKind kind() {
        return kind == null ? OfferKind.GOAL : kind;
    }

    public void kind(OfferKind kind) {
        this.kind = kind;
    }

    /**
     * @return the name of the offer, trimmed; empty for the name of the event
     */
    public String name() {
        return name == null ? "" : name.trim();
    }

    public void name(String name) {
        this.name = name;
    }

    /**
     * @return {@code minted} or the economy account paying the offer, trimmed
     */
    public String money() {
        return money == null ? "" : money.trim();
    }

    public void money(String money) {
        this.money = money;
    }

    /**
     * @return the material ordered, trimmed; empty when the offer orders criteria
     */
    public String material() {
        return material == null ? "" : material.trim();
    }

    public void material(String material) {
        this.material = material;
    }

    /**
     * @return the criteria ordered in the text form players type, trimmed; empty when the offer orders a material
     */
    public String criteria() {
        return criteria == null ? "" : criteria.trim();
    }

    public void criteria(String criteria) {
        this.criteria = criteria;
    }

    /**
     * @return the items asked for in all
     */
    public int amount() {
        return amount;
    }

    public void amount(int amount) {
        this.amount = amount;
    }

    /**
     * @return the price paid per item
     */
    public double price() {
        return price;
    }

    public void price(double price) {
        this.price = price;
    }

    /**
     * @return the items per chunk of a goal
     */
    public int chunk() {
        return chunk;
    }

    public void chunk(int chunk) {
        this.chunk = chunk;
    }

    /**
     * @return the chunks of a goal waiting at once
     */
    public int open() {
        return open;
    }

    public void open(int open) {
        this.open = open;
    }

    /**
     * @return the money of the first places of a contest, such as {@code 500+250+100}; empty for none
     */
    public String prizes() {
        return prizes == null ? "" : prizes.trim();
    }

    public void prizes(String prizes) {
        this.prizes = prizes;
    }

    /**
     * @return what the whole offer may spend, 0 for what it costs, prizes included
     */
    public double budget() {
        return Math.max(0, budget);
    }

    public void budget(double budget) {
        this.budget = budget;
    }
}
