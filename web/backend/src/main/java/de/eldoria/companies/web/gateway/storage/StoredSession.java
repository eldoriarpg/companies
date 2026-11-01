/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.storage;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A dashboard session as stored.
 *
 * @param id the session id, known to the plugin
 * @param serverId the server it belongs to
 * @param player the player's id
 * @param playerName the player's name
 * @param node the node the player asked for it on
 * @param state where it stands
 * @param shortId the short id of its link, while pending
 * @param codeHash the hash of its login code, while pending
 * @param attempts wrong codes entered so far
 * @param codeExpires when the code stops working, while pending
 * @param device the browser it is bound to, once bound
 * @param admin whether the plugin confirmed the login as one of a player with admin rights
 * @param adminFeatures the admin features the plugin allowed the player, null when it did not say
 * @param created when it was created
 * @param boundAt when a browser logged in
 * @param lastAccess when a browser used it last
 */
public record StoredSession(
        String id,
        String serverId,
        UUID player,
        String playerName,
        UUID node,
        SessionState state,
        @Nullable String shortId,
        @Nullable String codeHash,
        int attempts,
        @Nullable Instant codeExpires,
        @Nullable String device,
        boolean admin,
        @Nullable Set<String> adminFeatures,
        Instant created,
        @Nullable Instant boundAt,
        Instant lastAccess) {
    /**
     * @return what of the admin tools the session may see; {@link AdminAccess#NONE} for a player without admin rights
     */
    public AdminAccess access() {
        return admin ? new AdminAccess(true, adminFeatures) : AdminAccess.NONE;
    }
}
