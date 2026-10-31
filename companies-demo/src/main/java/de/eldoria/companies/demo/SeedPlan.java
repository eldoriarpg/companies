/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What the seed creates, read from the {@code seed} section of the config: companies with their owner and members,
 * item orders of demo players, and a market history of materials with their base price. An entry that can not be read
 * is reported and left out.
 *
 * @param companies  the companies to found
 * @param orders     the orders to place
 * @param market     the base unit price of each material of the market history, by material name
 * @param marketDays how many days back the market history reaches
 */
record SeedPlan(List<Company> companies, List<Order> orders, Map<String, Double> market, int marketDays) {
    /**
     * Copies the lists and the prices, keeping their order.
     */
    SeedPlan {
        companies = List.copyOf(companies);
        orders = List.copyOf(orders);
        market = Collections.unmodifiableMap(new LinkedHashMap<>(market));
    }

    /**
     * A company of the seed.
     *
     * @param name    its name
     * @param owner   the name of its owner
     * @param members the names of its other members
     */
    record Company(String name, String owner, List<String> members) {
        /**
         * Copies the members.
         */
        Company {
            members = List.copyOf(members);
        }
    }

    /**
     * An order of the seed.
     *
     * @param owner the name of the player who places it
     * @param name  its name
     * @param lines what it asks for
     */
    record Order(String owner, String name, List<Line> lines) {
        /**
         * Copies the lines.
         */
        Order {
            lines = List.copyOf(lines);
        }
    }

    /**
     * A line of an order.
     *
     * @param material the item
     * @param amount   how many
     * @param price    the price of one item
     */
    record Line(Material material, int amount, double price) {}

    /**
     * Reads the plan.
     *
     * @param seed    the {@code seed} section
     * @param problem told about every entry left out
     * @return the plan
     */
    static SeedPlan read(ConfigurationSection seed, Consumer<String> problem) {
        List<Company> companies = new ArrayList<>();
        for (var entry : seed.getMapList("companies")) {
            var name = text(entry, "name");
            var owner = text(entry, "owner");
            if (name.isEmpty() || owner.isEmpty()) {
                problem.accept("A company needs a name and an owner: " + entry);
                continue;
            }
            companies.add(new Company(name, owner, texts(entry.get("members"))));
        }
        List<Order> orders = new ArrayList<>();
        for (var entry : seed.getMapList("orders")) {
            var owner = text(entry, "owner");
            var name = text(entry, "name");
            var lines = lines(entry.get("lines"), problem);
            if (owner.isEmpty() || name.isEmpty() || lines.isEmpty()) {
                problem.accept("An order needs an owner, a name and a line: " + entry);
                continue;
            }
            orders.add(new Order(owner, name, lines));
        }
        Map<String, Double> market = new LinkedHashMap<>();
        var prices = seed.getConfigurationSection("market.prices");
        if (prices != null) {
            for (var key : prices.getKeys(false)) {
                var material = Material.matchMaterial(key);
                var price = prices.getDouble(key);
                if (material == null || !material.isItem() || !(price > 0)) {
                    problem.accept("A market price needs an item and a price: " + key);
                    continue;
                }
                market.put(material.name(), price);
            }
        }
        return new SeedPlan(
                companies, orders, market, Math.max(MarketSeeder.FIRST_DAY_BACK, seed.getInt("market.days", 30)));
    }

    private static List<Line> lines(Object raw, Consumer<String> problem) {
        List<Line> lines = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return lines;
        for (var item : list) {
            if (!(item instanceof Map<?, ?> line)) continue;
            var material = Material.matchMaterial(text(line, "material"));
            var amount = number(line.get("amount"));
            var price = number(line.get("price"));
            if (material == null || !material.isItem() || amount < 1 || !(price > 0)) {
                problem.accept("A line needs an item, an amount and a price: " + line);
                continue;
            }
            lines.add(new Line(material, (int) amount, price));
        }
        return lines;
    }

    private static String text(Map<?, ?> map, String key) {
        var value = map.get(key);
        return value == null ? "" : value.toString().strip();
    }

    private static List<String> texts(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream()
                .map(Object::toString)
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    private static double number(Object raw) {
        return raw instanceof Number number ? number.doubleValue() : 0;
    }
}
