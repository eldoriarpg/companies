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
 * Why a repeating order was stopped before its end.
 */
public enum SeriesStopReason implements Translatable {
    /**
     * The owner stopped it.
     */
    OWNER(0),
    /**
     * The owner cancelled a copy.
     */
    COPY_CANCELLED(1),
    /**
     * The owner could not pay a copy.
     */
    CANNOT_PAY(2),
    /**
     * An admin removed a copy.
     */
    ADMIN(3),
    /**
     * A copy can no longer be posted, for example because the order rules changed.
     */
    INVALID(4);

    private final int id;

    SeriesStopReason(int id) {
        this.id = id;
    }

    /**
     * The value with the given id.
     *
     * @param id stored id
     * @return value, or empty when no value has this id
     */
    public static Optional<SeriesStopReason> byId(int id) {
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
        return "enums.seriesStopReason." + name().toLowerCase(Locale.ROOT);
    }
}
