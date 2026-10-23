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
 * Whether a repeating order still posts copies.
 */
public enum SeriesState implements Translatable {
    /**
     * Copies are posted when they are due.
     */
    ACTIVE(0),
    /**
     * Stopped before its end; the owner can resume it.
     */
    STOPPED(1),
    /**
     * Posted its last copy or reached its end date.
     */
    FINISHED(2);

    private final int id;

    SeriesState(int id) {
        this.id = id;
    }

    /**
     * The value with the given id.
     *
     * @param id stored id
     * @return value, or empty when no value has this id
     */
    public static Optional<SeriesState> byId(int id) {
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
        return "enums.seriesState." + name().toLowerCase(Locale.ROOT);
    }
}
