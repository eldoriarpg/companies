/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.debug;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.nodes.DatabaseBus;
import de.eldoria.companies.nodes.NodeBus;
import de.eldoria.companies.nodes.RedisBus;
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
 * Adds the state of the storage, the economy, the node setup and the node bus to debug reports. The bus is described
 * by its state and server address, never by its credentials.
 */
public class CompaniesDebug implements DebugDataProvider {
    private static final int CONNECTION_TIMEOUT_SECONDS = 5;
    private final Plugin plugin;
    private final Configuration configuration;
    private final DataSource dataSource;
    private final NodeBus bus;

    /**
     * @param plugin        the plugin whose economy and hooks are checked
     * @param configuration configuration with the storage and node settings
     * @param dataSource    data source of the storage
     * @param bus           the node bus, the {@link DatabaseBus} before it was created
     */
    public CompaniesDebug(Plugin plugin, Configuration configuration, DataSource dataSource, NodeBus bus) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.dataSource = dataSource;
        this.bus = bus;
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
                            "Redis", redis(),
                            "PlaceholderAPI hook",
                                    String.valueOf(generalSettings.hooks().placeholderapi())))
        };
    }

    @Override
    public @NotNull List<HealthCheck> getHealthChecks() {
        if (bus.state() == NodeBus.State.OFF) return List.of(database(), economy());
        return List.of(database(), economy(), redisHealth());
    }

    private String redis() {
        if (!(bus instanceof RedisBus redisBus)) return bus.state().name();
        return bus.state().name() + " (" + redisBus.address() + ")";
    }

    private HealthCheck redisHealth() {
        if (bus.realtime()) return HealthCheck.ok("Redis", "Connected: " + redis());
        return HealthCheck.error("Redis", "Not connected, polling until it is back: " + redis());
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
