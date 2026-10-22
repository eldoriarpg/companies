/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/**
 * How players can become members of a company.
 */
public enum JoinMode implements Translatable {
    /**
     * Every player may join without asking.
     */
    OPEN(0),
    /**
     * Players apply and a member with the invite permission accepts or denies them.
     */
    APPLICATIONS(1),
    /**
     * Players join only when a member invited them.
     */
    INVITE_ONLY(2);

    private final int id;

    JoinMode(int id) {
        this.id = id;
    }

    /**
     * The join mode with the given id.
     *
     * @param id stored id
     * @return join mode, or empty when no mode has this id
     */
    public static Optional<JoinMode> byId(int id) {
        for (var mode : values()) if (mode.id == id) return Optional.of(mode);
        return Optional.empty();
    }

    /**
     * The stable id of this mode, as stored in the database.
     *
     * @return id
     */
    public int id() {
        return id;
    }

    @Override
    public @NotNull String translationKey() {
        return "enums.joinMode." + name().toLowerCase(Locale.ROOT);
    }
}
