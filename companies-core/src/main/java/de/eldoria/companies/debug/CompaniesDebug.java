/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.debug;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.nodes.DatabaseBus;
import de.eldoria.companies.nodes.NodeBus;
import de.eldoria.companies.nodes.PostgresNotifyBus;
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
import java.util.stream.Collectors;

import javax.sql.DataSource;

/**
 * Adds the state of the storage, the economy, the node setup, the node bus and the settings waiting for a restart to
 * debug reports. The bus is described by how messages travel (Redis, low latency or database polling), its state and
 * the Redis address, never by credentials.
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
                            "Message blocking", String.valueOf(configuration.messageBlocking()),
                            "Node bus", bus(),
                            "PlaceholderAPI hook",
                                    String.valueOf(configuration.hooks().placeholderapi()),
                            "Restart pending", restartPending()))
        };
    }

    private String restartPending() {
        var pending = configuration.restartPending();
        if (pending.isEmpty()) return "no";
        return pending.stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    @Override
    public @NotNull List<HealthCheck> getHealthChecks() {
        if (bus.state() == NodeBus.State.OFF) return List.of(database(), economy());
        return List.of(database(), economy(), busHealth());
    }

    /**
     * Describes a bus for the report.
     *
     * @param bus the bus
     * @return for example {@code redis DISCONNECTED (redis.internal:6379)}
     */
    static String describe(NodeBus bus) {
        if (bus.state() == NodeBus.State.OFF) return NodeBus.Transport.DATABASE.id();
        var description = bus.transport().id() + " " + bus.state().name();
        if (bus instanceof RedisBus redis) return description + " (" + redis.address() + ")";
        if (bus instanceof PostgresNotifyBus notify) return description + " (" + notify.address() + ")";
        return description;
    }

    private String bus() {
        return describe(bus);
    }

    private HealthCheck busHealth() {
        if (bus.realtime()) return HealthCheck.ok("Node bus", "Connected: " + bus());
        return HealthCheck.error("Node bus", "Not connected, polling until it is back: " + bus());
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
