/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A perk companies can buy, as registered with {@code companies().registerPerk(...)}. The price and minimum level are
 * the defaults; the server overrides them per perk in {@code perks.yml}.
 *
 * @param id          the id of the perk: lower case letters, digits and dashes, at most 64 characters
 * @param name        the name shown to players, plain text
 * @param description what the perk gives, plain text
 * @param price       what it costs, paid once from the company's treasury
 * @param minLevel    the company level from which it can be bought and used
 */
public record PerkDefinition(String id, String name, String description, double price, int minLevel) {
    private static final Pattern ID = Pattern.compile("[a-z0-9-]{1,64}");

    /**
     * Checks the id and the defaults.
     *
     * @throws IllegalArgumentException when the id is not valid, the price is negative or not finite, or the level is
     *     below 1
     */
    public PerkDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        id = id.toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Not a perk id: " + id);
        if (!(price >= 0) || !Double.isFinite(price))
            throw new IllegalArgumentException("A perk price can not be negative: " + price);
        if (minLevel < 1) throw new IllegalArgumentException("A perk level starts at 1: " + minLevel);
    }
}
