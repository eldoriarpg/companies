/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.series;

import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/**
 * Why a due copy of a repeating order is held back.
 */
public enum SeriesWait implements Translatable {
    /**
     * The owner has as many active orders as their limit allows.
     */
    ORDER_LIMIT(0),
    /**
     * The previous copy still waits for a company.
     */
    PREVIOUS_OPEN(1);

    private final int id;

    SeriesWait(int id) {
        this.id = id;
    }

    /**
     * The value with the given id.
     *
     * @param id stored id
     * @return value, or empty when no value has this id
     */
    public static Optional<SeriesWait> byId(int id) {
        for (var value : values()) if (value.id == id) return Optional.of(value);
        return Optional.empty();
    }

    /**
     * The stable id, as stored in the database.
     *
     * @return id
     */
    public int id() {
        return id;
    }

    @Override
    public @NotNull String translationKey() {
        return "enums.seriesWait." + name().toLowerCase(Locale.ROOT);
    }
}
