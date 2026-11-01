/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The players of the seed. They never join; each name gets the id an offline-mode server gives it, so the same name
 * is the same player after every reset.
 */
final class DemoPlayers {
    private DemoPlayers() {}

    /**
     * @param name the name of a demo player
     * @return the player's id
     */
    static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
