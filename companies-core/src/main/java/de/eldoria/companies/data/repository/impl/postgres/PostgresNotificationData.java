/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNotificationData;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;

public class PostgresNotificationData extends MariaDbNotificationData {
    /**
     * Create a new QueryFactoryholder
     *
     * @param dataSource      datasource
     * @param executorService executor for futures
     */
    public PostgresNotificationData(DataSource dataSource, Plugin plugin, ExecutorService executorService) {
        super(dataSource, plugin, executorService);
    }
}
