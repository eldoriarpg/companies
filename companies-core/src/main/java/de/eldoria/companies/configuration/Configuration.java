/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.DatabaseSettings;
import de.eldoria.companies.configuration.elements.GeneralSettings;
import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.eldoutilities.config.ConfigKey;
import de.eldoria.eldoutilities.config.JacksonConfig;
import de.eldoria.eldoutilities.config.exceptions.ConfigurationException;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Configuration extends JacksonConfig<ConfigFile> {
    public static final ConfigKey<ConfigFile> CONFIG_YML = ConfigKey.defaultConfig(ConfigFile.class, ConfigFile::new);
    public static final ConfigKey<DatabaseSettings> DATABASE = ConfigKey.of("Database config", Path.of("database.yml"), DatabaseSettings.class, DatabaseSettings::new);
    public static final ConfigKey<NodeSettings> NODE_SETTINGS = ConfigKey.of("Node config", Path.of("node.yml"), NodeSettings.class, NodeSettings::new);
    public static final ConfigKey<OrderSettings> ORDER_SETTINGS = ConfigKey.of("Order config", Path.of("order.yml"), OrderSettings.class, OrderSettings::new);
    public static final ConfigKey<UserSettings> USER_SETTINGS = ConfigKey.of("User config", Path.of("user.yml"), UserSettings.class, UserSettings::new);
    public static final ConfigKey<CompanySettings> COMPANY_SETTINGS = ConfigKey.of("Company config", Path.of("company.yml"), CompanySettings.class, CompanySettings::new);

    public Configuration(Plugin plugin) {
        super(plugin, CONFIG_YML);
    }

    public CompanySettings companySettings() {
        return secondary(COMPANY_SETTINGS);
    }

    public UserSettings userSettings() {
        return secondary(USER_SETTINGS);
    }

    public OrderSettings orderSetting() {
        return secondary(ORDER_SETTINGS);
    }

    public GeneralSettings generalSettings() {
        return main().generalSettings();
    }

    public DatabaseSettings databaseSettings() {
        return secondary(DATABASE);
    }

    public NodeSettings nodeSettings() {
        return secondary(NODE_SETTINGS);
    }

    public CompletableFuture<Void> syncConfigurations(ANodeData nodeData) {
        return CompletableFuture.runAsync(() -> {
            // Setup database shards
            var nodeSettings = nodeSettings();
            nodeData.updateNode(plugin());
            nodeData.assertPrimaryNode(plugin());

            if(databaseSettings().storageType() == DatabaseSettings.StorageType.SQLITE && nodeSettings.nodeType() == NodeType.SECONDARY){
                throw new IllegalStateException("Secondary nodes can not be used with SqLite.");
            }

            // List configurations to sync
            List<ConfigKey<?>> sync = List.of(Configuration.COMPANY_SETTINGS, Configuration.USER_SETTINGS, Configuration.ORDER_SETTINGS, Configuration.CONFIG_YML);

            for (ConfigKey<?> configKey : sync) {
                if (nodeSettings.nodeType() == NodeType.PRIMARY) {
                    // primary nodes always write into database
                    nodeData.savePrimaryConfiguration(configKey, this);
                } else {
                    // secondary nodes always override their settings with database values
                    replace(configKey, nodeData.loadPrimaryConfiguration(configKey, this));
                }
            }
        });
    }
}
