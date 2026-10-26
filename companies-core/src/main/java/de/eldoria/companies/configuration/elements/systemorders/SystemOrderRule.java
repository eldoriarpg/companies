/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.systemorders;

/**
 * One rule of {@code system-orders.yml}: an item the server orders again and again, at a price and from a money
 * source of its own. Such an order may compete with the orders of players.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class SystemOrderRule {
    /**
     * The longest id a rule may have, as the orders store it.
     */
    public static final int MAX_ID_LENGTH = 64;

    private String id = "";
    private String name = "";
    private boolean enabled = true;
    private String material = "";
    private int amount = 64;
    private RulePrice price = new RulePrice();
    private int everyHours = 24;
    private int lifetimeHours = 12;
    private int maxOpen = 1;
    private MoneySource source = new MoneySource();

    public SystemOrderRule() {}

    /**
     * @param id       id of the rule
     * @param material item the rule orders
     */
    public SystemOrderRule(String id, String material) {
        this.id = id;
        this.material = material;
    }

    /**
     * The id orders and decisions keep to name the rule. Rules without one, or with one an earlier rule has, are
     * skipped.
     *
     * @return the id, trimmed
     */
    public String id() {
        return id == null ? "" : id.trim();
    }

    /**
     * The name of the orders, the id when none is set.
     *
     * @return the name
     */
    public String name() {
        return name == null || name.isBlank() ? id() : name;
    }

    public void name(String name) {
        this.name = name;
    }

    /**
     * Whether the rule places orders.
     *
     * @return true when it places orders
     */
    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The item ordered, a material name such as {@code IRON_INGOT}.
     * <p>
     * TODO allow an exact item template saved in game.
     *
     * @return the material name as written
     */
    public String material() {
        return material == null ? "" : material.trim();
    }

    /**
     * How many items one order asks for.
     *
     * @return the amount, at least 1
     */
    public int amount() {
        return Math.max(1, amount);
    }

    public void amount(int amount) {
        this.amount = amount;
    }

    /**
     * What one item is paid.
     *
     * @return the price rule
     */
    public RulePrice price() {
        return price == null ? new RulePrice() : price;
    }

    public void price(RulePrice price) {
        this.price = price;
    }

    /**
     * How long after one order the next one is placed.
     *
     * @return hours, at least 1
     */
    public int everyHours() {
        return Math.max(1, everyHours);
    }

    public void everyHours(int everyHours) {
        this.everyHours = everyHours;
    }

    /**
     * How long an order waits for a company before it is removed and its money goes back to the source.
     *
     * @return hours, at least 1
     */
    public int lifetimeHours() {
        return Math.max(1, lifetimeHours);
    }

    public void lifetimeHours(int lifetimeHours) {
        this.lifetimeHours = lifetimeHours;
    }

    /**
     * How many orders of the rule may be open or in work at once.
     *
     * @return orders, at least 1
     */
    public int maxOpen() {
        return Math.max(1, maxOpen);
    }

    public void maxOpen(int maxOpen) {
        this.maxOpen = maxOpen;
    }

    /**
     * Where the money of the orders comes from.
     *
     * @return the money source
     */
    public MoneySource source() {
        return source == null ? new MoneySource() : source;
    }

    public void source(MoneySource source) {
        this.source = source;
    }
}
