/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.dashboard;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A dashboard session a node of this network created. Every node can check it, so the gateway may route a session's
 * requests to any node. Times are UTC.
 *
 * @param id          the session id the gateway gave it
 * @param player      the player it belongs to
 * @param playerName  the player's name when it was created
 * @param node        the node that created it
 * @param created     when it was created
 * @param codeExpires when its login code stops working
 * @param lastActive  when it was last used
 * @param opened      when a browser logged in with the code, null while pending
 * @param device      the browser the gateway named, null when unknown
 * @param permissions the Bukkit permissions of the player that matter to the dashboard, as last seen online
 */
public record StoredSession(
        String id,
        UUID player,
        String playerName,
        UUID node,
        LocalDateTime created,
        LocalDateTime codeExpires,
        LocalDateTime lastActive,
        @Nullable LocalDateTime opened,
        @Nullable String device,
        Set<String> permissions) {
    /**
     * @return true once a browser logged in with the code
     */
    public boolean isOpen() {
        return opened != null;
    }

    /**
     * @return the browser the gateway named, empty when unknown
     */
    public Optional<String> deviceName() {
        return Optional.ofNullable(device);
    }

    /**
     * Joins the permissions for storage.
     *
     * @return the permissions, one per line
     */
    public String permissionText() {
        return String.join("\n", permissions);
    }

    /**
     * Splits stored permissions.
     *
     * @param text the permissions, one per line
     * @return the permissions
     */
    public static Set<String> permissions(@Nullable String text) {
        if (text == null || text.isBlank()) return Set.of();
        return Set.of(text.split("\n"));
    }
}
