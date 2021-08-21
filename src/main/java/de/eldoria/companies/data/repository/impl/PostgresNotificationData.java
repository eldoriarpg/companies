package de.eldoria.companies.data.repository.impl;

import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;

public class PostgresNotificationData extends MariaDbNotificationData {
    /**
     * Create a new QueryFactoryholder
     *
     * @param dataSource      datasource
     * @param executorService
     */
    public PostgresNotificationData(DataSource dataSource, Plugin plugin, ExecutorService executorService) {
        super(dataSource, plugin, executorService);
    }
}
