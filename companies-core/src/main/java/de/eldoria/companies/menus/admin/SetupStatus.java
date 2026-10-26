/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.configuration.elements.DatabaseSettings.StorageType;
import de.eldoria.companies.configuration.elements.NodeType;

import java.util.ArrayList;
import java.util.List;

/**
 * What the in-game setup shows about this server before anything is entered: the database, the network, the server's
 * role and name, and the dashboard, with the recommendations that follow from them. The database and the role are only
 * shown; they are changed in the files and need a restart.
 *
 * @param storage          the database type
 * @param nodes            the servers registered in the database
 * @param redis            whether Redis is configured
 * @param role             this server's role
 * @param name             this server's name, empty for none
 * @param dashboardEnabled whether the dashboard connects
 * @param connection       the dashboard connection's state as the admin reads it
 * @param keySet           whether the network has a dashboard key
 */
public record SetupStatus(
        StorageType storage,
        int nodes,
        boolean redis,
        NodeType role,
        String name,
        boolean dashboardEnabled,
        String connection,
        boolean keySet) {
    /**
     * @return the locale keys of what the admin should change, most important first
     */
    public List<String> recommendations() {
        var found = new ArrayList<String>();
        var network = nodes > 1;
        if (network && storage == StorageType.SQLITE) found.add("companyadmin.setup.recommend.sqliteNetwork");
        if (network && storage == StorageType.MARIADB) found.add("companyadmin.setup.recommend.postgres");
        if (network && !redis) found.add("companyadmin.setup.recommend.redis");
        if (!dashboardEnabled) found.add("companyadmin.setup.recommend.dashboard");
        if (dashboardEnabled && !keySet) found.add("companyadmin.setup.recommend.key");
        return found;
    }
}
