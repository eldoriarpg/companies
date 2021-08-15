package de.eldoria.companies.data.repository.impl;

import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;

public class PostgresCompanyData extends MariaDbCompanyData{
    public PostgresCompanyData(DataSource dataSource, Plugin plugin, ExecutorService executorService) {
        super(dataSource, plugin, executorService);
    }
}
