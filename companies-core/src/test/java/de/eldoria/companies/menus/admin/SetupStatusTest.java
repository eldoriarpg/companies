/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.configuration.elements.DatabaseSettings.StorageType;
import de.eldoria.companies.configuration.elements.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetupStatusTest {
    private static SetupStatus status(StorageType storage, int nodes, boolean redis, boolean dashboard, boolean key) {
        return new SetupStatus(storage, nodes, redis, NodeType.PRIMARY, "", dashboard, "connected", key);
    }

    @Test
    void aSingleServerOnlyNeedsTheDashboard() {
        assertEquals(
                List.of("companyadmin.setup.recommend.dashboard"),
                status(StorageType.SQLITE, 1, false, false, false).recommendations());
        assertEquals(
                List.of("companyadmin.setup.recommend.key"),
                status(StorageType.SQLITE, 1, false, true, false).recommendations());
        assertEquals(List.of(), status(StorageType.SQLITE, 1, false, true, true).recommendations());
    }

    @Test
    void aNetworkIsToldAboutItsDatabaseAndRedis() {
        assertEquals(
                List.of("companyadmin.setup.recommend.sqliteNetwork", "companyadmin.setup.recommend.redis"),
                status(StorageType.SQLITE, 2, false, true, true).recommendations());
        assertEquals(
                List.of("companyadmin.setup.recommend.postgres"),
                status(StorageType.MARIADB, 3, true, true, true).recommendations());
        assertEquals(
                List.of(), status(StorageType.POSTGRES, 3, true, true, true).recommendations());
    }
}
