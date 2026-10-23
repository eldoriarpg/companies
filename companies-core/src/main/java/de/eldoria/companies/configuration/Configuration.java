/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import de.eldoria.companies.configuration.elements.BoardSettings;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.DatabaseSettings;
import de.eldoria.companies.configuration.elements.DiscordSettings;
import de.eldoria.companies.configuration.elements.EconomySettings;
import de.eldoria.companies.configuration.elements.GeneralSettings;
import de.eldoria.companies.configuration.elements.MenuSettings;
import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.PlaceholderSettings;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.configuration.elements.TopSettings;
import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.eldoutilities.config.JacksonConfig;
import dev.chojo.ocular.key.Key;
import org.bukkit.plugin.Plugin;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class Configuration extends JacksonConfig<ConfigFile> {
    public static final Key<ConfigFile> CONFIG_YML = Key.builder(Path.of("config.yml"), ConfigFile::fresh)
            .name("Main config")
            .build();
    public static final Key<DatabaseSettings> DATABASE = Key.builder(Path.of("database.yml"), DatabaseSettings::new)
            .name("Database config")
            .build();
    public static final Key<NodeSettings> NODE_SETTINGS = Key.builder(Path.of("node.yml"), NodeSettings::new)
            .name("Node config")
            .build();
    public static final Key<OrderSettings> ORDER_SETTINGS = Key.builder(Path.of("order.yml"), OrderSettings::new)
            .name("Order config")
            .build();
    public static final Key<UserSettings> USER_SETTINGS = Key.builder(Path.of("user.yml"), UserSettings::new)
            .name("User config")
            .build();
    public static final Key<CompanySettings> COMPANY_SETTINGS = Key.builder(
                    Path.of("company.yml"), CompanySettings::new)
            .name("Company config")
            .build();

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

    public StyleSettings styleSettings() {
        return main().style();
    }

    /**
     * @return how menus are shown
     */
    public MenuSettings menuSettings() {
        return main().menus();
    }

    public EconomySettings economySettings() {
        return main().economy();
    }

    /**
     * @return how notifications are kept
     */
    public NotificationSettings notificationSettings() {
        return main().notifications();
    }

    /**
     * @return how the PlaceholderAPI values are cached
     */
    public PlaceholderSettings placeholderSettings() {
        return main().placeholders();
    }

    /**
     * @return how the company ranking is cached
     */
    public TopSettings topSettings() {
        return main().top();
    }

    /**
     * @return the order boards on signs and NPCs
     */
    public BoardSettings boardSettings() {
        return main().boards();
    }

    /**
     * @return the Discord webhook
     */
    public DiscordSettings discordSettings() {
        return main().discord();
    }

    public DatabaseSettings databaseSettings() {
        return secondary(DATABASE);
    }

    public NodeSettings nodeSettings() {
        return secondary(NODE_SETTINGS);
    }

    public CompletableFuture<Void> syncConfigurations(ANodeData nodeData) {
        return CompletableFuture.runAsync(() -> {
            var nodeSettings = nodeSettings();
            save(NODE_SETTINGS);
            nodeData.updateNode(plugin());
            nodeData.assertPrimaryNode(plugin());

            if (databaseSettings().storageType() == DatabaseSettings.StorageType.SQLITE
                    && nodeSettings.nodeType() == NodeType.SECONDARY) {
                throw new IllegalStateException("Secondary nodes can not be used with SqLite.");
            }

            plugin().getLogger().info("Plugin is running as " + nodeData.nodeType() + " node.");
            sync(nodeData, COMPANY_SETTINGS);
            sync(nodeData, USER_SETTINGS);
            sync(nodeData, ORDER_SETTINGS);
            sync(nodeData, CONFIG_YML);
        });
    }

    private <V> void sync(ANodeData nodeData, Key<V> configKey) {
        if (nodeSettings().nodeType() == NodeType.PRIMARY) {
            plugin().getLogger().info("Syncing " + configKey.path() + " to database.");
            nodeData.savePrimaryConfiguration(configKey, this);
        } else {
            plugin().getLogger().info("Syncing " + configKey.path() + " from database.");
            replace(configKey, nodeData.loadPrimaryConfiguration(configKey, this));
            save(configKey);
        }
    }

    /**
     * Creates a JSON mapper for stored data, configured like the configuration files and with the Paper module, so
     * item stacks are written as Paper's byte representation.
     *
     * @return json mapper
     */
    @SuppressWarnings("unchecked")
    public JsonMapper storageMapper() {
        var builder = JsonMapper.builder();
        configure((MapperBuilder<ObjectMapper, ?>) (MapperBuilder<?, ?>) builder);
        additionalModules().forEach(builder::addModule);
        return builder.build();
    }
}
