/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.debug;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.debug.DebugDataProvider;
import de.eldoria.eldoutilities.debug.HealthCheck;
import de.eldoria.eldoutilities.debug.data.EntryData;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

/**
 * Adds the state of the storage, the economy and the node setup to debug reports.
 */
public class CompaniesDebug implements DebugDataProvider {
    private static final int CONNECTION_TIMEOUT_SECONDS = 5;
    private final Plugin plugin;
    private final Configuration configuration;
    private final DataSource dataSource;

    /**
     * @param plugin        the plugin whose economy and hooks are checked
     * @param configuration configuration with the storage and node settings
     * @param dataSource    data source of the storage
     */
    public CompaniesDebug(Plugin plugin, Configuration configuration, DataSource dataSource) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.dataSource = dataSource;
    }

    @Override
    public @NotNull EntryData[] getDebugInformations() {
        var nodeSettings = configuration.nodeSettings();
        var generalSettings = configuration.generalSettings();
        return new EntryData[] {
            new EntryData(
                    "Setup",
                    Map.of(
                            "Storage",
                                    configuration
                                            .databaseSettings()
                                            .storageType()
                                            .name(),
                            "Node type", nodeSettings.nodeType().name(),
                            "Node id", nodeSettings.nodeUid().toString(),
                            "Message blocking", String.valueOf(generalSettings.messageBlocking()),
                            "PlaceholderAPI hook",
                                    String.valueOf(generalSettings.hooks().placeholderapi())))
        };
    }

    @Override
    public @NotNull List<HealthCheck> getHealthChecks() {
        return List.of(database(), economy());
    }

    private HealthCheck database() {
        var storage = configuration.databaseSettings().storageType().name();
        try (var connection = dataSource.getConnection()) {
            if (connection.isValid(CONNECTION_TIMEOUT_SECONDS)) {
                return HealthCheck.ok("Database", "Connected to " + storage);
            }
            return HealthCheck.error("Database", "Connection to " + storage + " is not valid");
        } catch (SQLException e) {
            return HealthCheck.error("Database", "Could not connect to " + storage + ": " + e.getMessage());
        }
    }

    private HealthCheck economy() {
        var registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return HealthCheck.error("Economy", "No Vault economy provider is registered");
        }
        return HealthCheck.ok(
                "Economy",
                registration.getProvider().getName() + " provided by "
                        + registration.getPlugin().getName());
    }
}
