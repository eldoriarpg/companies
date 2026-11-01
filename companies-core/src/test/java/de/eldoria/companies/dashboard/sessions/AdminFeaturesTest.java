/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.sessions;

import de.eldoria.companies.dashboard.views.DashboardReads;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.web.api.Features;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminFeaturesTest {
    @Test
    void knowsThePermissionsOfEveryAdminFeature() {
        DashboardReads.FEATURES.stream()
                .filter(feature -> feature.startsWith(Features.ADMIN_PREFIX))
                .forEach(feature -> assertTrue(AdminFeatures.NEEDS.containsKey(feature), feature));
    }

    @Test
    void leavesOutTheToolsAnAdminIsDenied() {
        var allowed = AdminFeatures.allowed(Set.of(Admin.ADMIN, Admin.ORDERS, Admin.EVENTS));
        assertTrue(allowed.contains(Features.ADMIN_ORDERS));
        assertTrue(allowed.contains(Features.ADMIN_SETTINGS));
        assertFalse(allowed.contains(Features.ADMIN_CONFIG));
        assertFalse(allowed.contains(Features.ADMIN_NODES));
        assertFalse(allowed.contains(Features.ADMIN_COMPANIES));
    }

    @Test
    void unlocksAFeatureWithAnyOfItsPermissions() {
        assertEquals(List.of(Features.ADMIN_LEVELS), AdminFeatures.allowed(Set.of(Admin.CALC_LEVEL)));
        assertEquals(List.of(Features.ADMIN_PLAYERS), AdminFeatures.allowed(Set.of(Admin.GRANT)));
    }

    @Test
    void allowsNothingWithoutAdminPermissions() {
        assertTrue(AdminFeatures.allowed(Set.of()).isEmpty());
    }
}
