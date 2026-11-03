/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.perks;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The perks companies can buy, the file {@code perks.yml}: per perk id whether it is offered, its price and its
 * minimum level. A perk not listed is offered with the defaults it was registered with; a value left out takes the
 * default as well. What companies own is stored in the database.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class PerkSettings {
    private Map<String, Perk> perks = new LinkedHashMap<>();

    public PerkSettings() {}

    /**
     * @param id id of a perk
     * @return its settings, matched ignoring case; empty when it is not listed
     */
    public Optional<Perk> perk(String id) {
        if (perks == null) return Optional.empty();
        var key = id.toLowerCase(Locale.ROOT);
        for (var entry : perks.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(key)) return Optional.ofNullable(entry.getValue());
        }
        return Optional.empty();
    }

    /**
     * @param id   id of a perk
     * @param perk its settings
     */
    public void perk(String id, Perk perk) {
        if (perks == null) perks = new LinkedHashMap<>();
        perks.put(id.toLowerCase(Locale.ROOT), perk);
    }

    /**
     * The settings of one perk.
     */
    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
    public static class Perk {
        private boolean enabled = true;
        private @Nullable Double price;
        private @Nullable Integer minLevel;

        public Perk() {}

        /**
         * @param enabled  whether it is offered
         * @param price    its price, null for the default
         * @param minLevel its minimum level, null for the default
         */
        public Perk(boolean enabled, @Nullable Double price, @Nullable Integer minLevel) {
            this.enabled = enabled;
            this.price = price;
            this.minLevel = minLevel;
        }

        /**
         * @return whether companies may buy and use it; owned perks stay owned but paused while it is off
         */
        public boolean enabled() {
            return enabled;
        }

        /**
         * @return its price, empty for the default it was registered with
         */
        public Optional<Double> price() {
            return Optional.ofNullable(price).filter(value -> value >= 0 && Double.isFinite(value));
        }

        /**
         * @return the company level from which it can be bought and used, empty for the default
         */
        public Optional<Integer> minLevel() {
            return Optional.ofNullable(minLevel).filter(value -> value >= 1);
        }
    }
}
