/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.sessions;

import de.eldoria.companies.dashboard.views.DashboardReads;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.web.api.Features;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which admin tools of the dashboard a player may see: each admin feature with the permissions of which one unlocks it,
 * the same the plugin checks when the tool is used. The gateway offers a session only these, so an admin does not see
 * tools that would refuse them.
 */
public final class AdminFeatures {
    /**
     * The permissions that unlock each admin feature, any one of them enough.
     */
    static final Map<String, List<String>> NEEDS = Map.ofEntries(
            Map.entry(Features.ADMIN_ORDERS, List.of(Admin.ORDERS)),
            Map.entry(Features.ADMIN_PLAYERS, List.of(Admin.PLAYER, Admin.GRANT)),
            Map.entry(
                    Features.ADMIN_COMPANIES,
                    List.of(Admin.TREASURY, Admin.RENAME, Admin.TRANSFER_OWNER, Admin.DELETE)),
            Map.entry(Features.ADMIN_LEVELS, List.of(Admin.LEVEL, Admin.CALC_LEVEL)),
            Map.entry(Features.ADMIN_BOARDS, List.of(Admin.BOARDS)),
            Map.entry(Features.ADMIN_NODES, List.of(Admin.NODES)),
            Map.entry(Features.ADMIN_CONFIG, List.of(Admin.RELOAD)),
            Map.entry(Features.ADMIN_SETTINGS, List.of(Admin.ADMIN)),
            Map.entry(Features.ADMIN_SETUP, List.of(Admin.ADMIN)),
            Map.entry(Features.ADMIN_SYSTEM_ORDERS, List.of(Admin.ORDERS)),
            Map.entry(Features.ADMIN_OFFERS, List.of(Admin.OFFERS)),
            Map.entry(Features.ADMIN_EVENTS, List.of(Admin.EVENTS)),
            Map.entry(Features.ADMIN_JOBS, List.of(Admin.JOBS)));

    private AdminFeatures() {}

    /**
     * @param permissions the permissions remembered with a session, see {@link SessionPermissions#of}
     * @return the admin features of this version those permissions unlock, in the order of
     *     {@link DashboardReads#FEATURES}
     */
    public static List<String> allowed(Set<String> permissions) {
        return DashboardReads.FEATURES.stream()
                .filter(feature -> feature.startsWith(Features.ADMIN_PREFIX))
                .filter(feature ->
                        NEEDS.getOrDefault(feature, List.of()).stream().anyMatch(permissions::contains))
                .toList();
    }
}
