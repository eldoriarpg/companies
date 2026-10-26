/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

/**
 * The owner of the orders the server places itself. It is a reserved id of version 0, which neither Mojang nor an
 * offline server ever hands out, so it never belongs to a player.
 * <p>
 * Everything that would reach the owner of an order skips it: no notifications, no items to receive (a delivered
 * system order is completed at once and its items are removed), and refunds go back to where its money came from.
 */
public final class SystemOwner {
    /**
     * The reserved owner id of system orders.
     */
    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000005e7e7");

    /**
     * The name shown where a player's name would be.
     */
    public static final String NAME = "Server";

    private SystemOwner() {}

    /**
     * @param id     a player's or the server's id
     * @param lookup the name of a player by id, null when unknown
     * @return the name to show: {@link #NAME} for the server, the player's name, or the id when the name is unknown
     */
    public static String name(UUID id, Function<UUID, @Nullable String> lookup) {
        if (is(id)) return NAME;
        var name = lookup.apply(id);
        return name == null ? id.toString() : name;
    }

    /**
     * @param owner an order's owner
     * @return whether it is the server
     */
    public static boolean is(UUID owner) {
        return ID.equals(owner);
    }
}
