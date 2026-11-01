/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.storage;

import de.eldoria.companies.web.api.Features;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * What of the admin tools a session may see, as the plugin confirmed with the login.
 *
 * @param admin whether the player has admin rights at all
 * @param features the admin features the player's permissions allow; null from plugins that do not say, which leaves
 *     every admin feature to an admin
 */
public record AdminAccess(boolean admin, @Nullable Set<String> features) {
    /**
     * No admin rights.
     */
    public static final AdminAccess NONE = new AdminAccess(false, Set.of());

    /**
     * Copies the features.
     */
    public AdminAccess {
        features = features == null ? null : Set.copyOf(features);
    }

    /**
     * @param feature a feature of the server
     * @return whether the session may see it: every feature that is not an admin feature, and an admin feature for an
     *     admin whose permissions allow it
     */
    public boolean allows(String feature) {
        if (!feature.startsWith(Features.ADMIN_PREFIX)) return true;
        return admin && (features == null || features.contains(feature));
    }
}
