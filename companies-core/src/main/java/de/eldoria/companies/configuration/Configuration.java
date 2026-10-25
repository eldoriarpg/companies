/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import de.eldoria.companies.configuration.elements.BoardBindings;
import de.eldoria.companies.configuration.elements.BoardSettings;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.DashboardSettings;
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
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.config.JacksonConfig;
import dev.chojo.ocular.exceptions.ConfigurationException;
import dev.chojo.ocular.key.Key;
import org.bukkit.plugin.Plugin;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class Configuration extends JacksonConfig<ConfigFile> {
    public static final Key<ConfigFile> CONFIG_YML = Key.builder(Path.of("config.yml"), ConfigFile::fresh)
            .name("Main config")
            .strict()
            .build();
    public static final Key<DatabaseSettings> DATABASE = Key.builder(Path.of("database.yml"), DatabaseSettings::new)
            .name("Database config")
            .build();
    public static final Key<NodeSettings> NODE_SETTINGS = Key.builder(Path.of("node.yml"), NodeSettings::new)
            .name("Node config")
            .build();
    public static final Key<OrderSettings> ORDER_SETTINGS = Key.builder(Path.of("order.yml"), OrderSettings::new)
            .name("Order config")
            .strict()
            .build();
    public static final Key<UserSettings> USER_SETTINGS = Key.builder(Path.of("user.yml"), UserSettings::new)
            .name("User config")
            .strict()
            .build();
    public static final Key<CompanySettings> COMPANY_SETTINGS = Key.builder(
                    Path.of("company.yml"), CompanySettings::new)
            .name("Company config")
            .strict()
            .build();
    public static final Key<BoardBindings> BOARD_BINDINGS = Key.builder(Path.of("boards.yml"), BoardBindings::new)
            .name("Board bindings")
            .build();

    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final YAMLMapper YAML = YAMLMapper.builder().build();
    private static final String APPLIED_FOLDER = ".synced";

    private final Set<Key<?>> unreadable = ConcurrentHashMap.newKeySet();

    public Configuration(Plugin plugin) {
        super(plugin, CONFIG_YML);
    }

    /**
     * Reads a file the first time it is needed. A shared file that cannot be read, see {@link #unreadable(Key)}, stays
     * as it is on disk and its defaults are used meanwhile.
     */
    @Override
    public synchronized <V> V secondary(Key<V> key) {
        var first = !loaded(key);
        V value;
        try {
            value = super.secondary(key);
        } catch (ConfigurationException e) {
            if (!key.strict()) throw e;
            markUnreadable(key, e);
            var defaults = key.initValue().get();
            super.replace(key, defaults);
            return defaults;
        }
        if (first && loaded(key)) afterRead(key, value);
        return value;
    }

    /**
     * Reads a file again. A shared file that cannot be read keeps the values it had and stays unreadable, see
     * {@link #unreadable(Key)}, until a reload reads it.
     */
    @Override
    public void reload(Key<?> key) {
        try {
            super.reload(key);
        } catch (ConfigurationException e) {
            if (!key.strict()) throw e;
            markUnreadable(key, e);
            return;
        }
        if (unreadable.remove(key)) plugin().getLogger().info(key.path() + " can be read again.");
        afterRead(key, secondary(key));
    }

    @Override
    public <V> void replace(Key<V> key, V newValue) {
        if (newValue instanceof CompanySettings settings) settings.renumberLevels();
        unreadable.remove(key);
        super.replace(key, newValue);
    }

    /**
     * Writes a file, unless it could not be read: its values in use are then defaults or older ones, and writing them
     * would overwrite what the admin is fixing.
     */
    @Override
    public void save(Key<?> key) {
        if (unreadable.contains(key)) {
            plugin().getLogger()
                    .warning("Not saving " + key.path() + ", since it could not be read. Fix it and reload.");
            return;
        }
        super.save(key);
    }

    /**
     * Whether a file the network shares ({@code config.yml}, {@code company.yml}, {@code user.yml}, {@code order.yml})
     * could not be read the last time. Such a file is left as it is instead of being replaced with defaults, is not
     * saved and is not shared with the other nodes, until a reload reads it again.
     *
     * @param key the file
     * @return true while it cannot be read
     */
    public boolean unreadable(Key<?> key) {
        return unreadable.contains(key);
    }

    /**
     * @return the files that cannot be read now, see {@link #unreadable(Key)}
     */
    public List<String> unreadableFiles() {
        return unreadable.stream().map(key -> key.path().toString()).sorted().toList();
    }

    private void markUnreadable(Key<?> key, ConfigurationException e) {
        unreadable.add(key);
        plugin().getLogger()
                .log(
                        Level.SEVERE,
                        key.path() + " cannot be read and is left as it is. Its last values are used, or its defaults"
                                + " right after a start, and it is neither saved nor shared with other servers until"
                                + " it is fixed and reloaded.",
                        e);
    }

    /**
     * Numbers the levels of a freshly read {@code company.yml} 1 to n in list order. When a number changed, the file
     * is written again and a warning tells the admin.
     *
     * @param key   key of the file that was read
     * @param value the content read
     */
    private void afterRead(Key<?> key, Object value) {
        if (!(value instanceof CompanySettings settings) || !settings.renumberLevels()) return;
        plugin().getLogger()
                .warning("The levels in " + key.path() + " were not numbered 1 to "
                        + settings.level().size()
                        + " in list order. They were renumbered in list order and the file was saved.");
        save(key);
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
     * @return the NPCs bound to boards on this node, never synced between nodes
     */
    public BoardBindings boardBindings() {
        return secondary(BOARD_BINDINGS);
    }

    /**
     * Writes the NPCs bound to boards to {@code boards.yml}.
     */
    public void saveBoardBindings() {
        save(BOARD_BINDINGS);
    }

    /**
     * @return the Discord webhook
     */
    public DiscordSettings discordSettings() {
        return main().discord();
    }

    /**
     * @return the web dashboard
     */
    public DashboardSettings dashboardSettings() {
        return main().dashboard();
    }

    public DatabaseSettings databaseSettings() {
        return secondary(DATABASE);
    }

    public NodeSettings nodeSettings() {
        return secondary(NODE_SETTINGS);
    }

    /**
     * Writes the shared files into the database on the primary, or takes the primary's copies on a secondary.
     *
     * @param nodeData the node repository holding the copies
     * @return whether a secondary's files changed, false on the primary and when every copy matched
     */
    public CompletableFuture<Boolean> syncConfigurations(ANodeData nodeData) {
        return CompletableFuture.supplyAsync(() -> {
            var nodeSettings = nodeSettings();
            save(NODE_SETTINGS);
            nodeData.updateNode(plugin());
            nodeData.assertPrimaryNode(plugin());

            if (databaseSettings().storageType() == DatabaseSettings.StorageType.SQLITE
                    && nodeSettings.nodeType() == NodeType.SECONDARY) {
                throw new IllegalStateException("Secondary nodes can not be used with SqLite.");
            }

            plugin().getLogger().info("Plugin is running as " + nodeData.nodeType() + " node.");
            var changed = sync(nodeData, COMPANY_SETTINGS);
            changed |= sync(nodeData, USER_SETTINGS);
            changed |= sync(nodeData, ORDER_SETTINGS);
            changed |= sync(nodeData, CONFIG_YML);
            return changed;
        });
    }

    /**
     * Writes a file into the database on the primary, or takes the primary's copy on a secondary, saves it and reads it
     * again, which applies the environment overrides of this node. A value set through an environment variable is part
     * of neither copy.
     *
     * @param nodeData  the node repository holding the copies
     * @param configKey the file
     * @param <V>       type of the file's content
     */
    private <V> boolean sync(ANodeData nodeData, Key<V> configKey) {
        if (nodeSettings().nodeType() != NodeType.PRIMARY) return takePrimaryCopy(nodeData, configKey);
        secondary(configKey);
        if (unreadable(configKey)) {
            plugin().getLogger().warning("Not syncing " + configKey.path() + " to database, since it cannot be read.");
            return false;
        }
        plugin().getLogger().info("Syncing " + configKey.path() + " to database.");
        nodeData.savePrimaryConfiguration(configKey, this);
        return false;
    }

    /**
     * Takes the primary's copy of a shared file on a secondary: saves it, reads it again, which applies this node's
     * environment overrides, and remembers what was written. A file that matches the copy is left alone. A file that
     * changed on this node since it last took a copy, or one never taken, is kept as
     * {@code backup_<time>_<file>} first; when that backup cannot be written, the file stays and nothing is taken.
     *
     * @param nodeData  the node repository holding the copies
     * @param configKey the file
     * @param <V>       type of the file's content
     * @return whether the file changed
     */
    public <V> boolean takePrimaryCopy(ANodeData nodeData, Key<V> configKey) {
        var copy = nodeData.primaryContent(configKey)
                .orElseThrow(() -> new IllegalStateException(
                        "No configuration present. Did you start the main node once already?"));
        var file = plugin().getDataFolder().toPath().resolve(configKey.path());
        if (tree(copy).equals(tree(file))) {
            rememberApplied(file);
            return false;
        }
        if (Files.exists(file) && editedHere(file) && !keepBackup(file)) return false;
        plugin().getLogger().info("Syncing " + configKey.path() + " from database.");
        replace(configKey, nodeData.loadPrimaryConfiguration(configKey, this));
        save(configKey);
        reload(configKey);
        rememberApplied(file);
        return true;
    }

    private boolean editedHere(Path file) {
        var applied = appliedRecord(file);
        try {
            return !Files.exists(applied) || !Files.readString(applied).equals(fingerprint(file));
        } catch (IOException e) {
            return true;
        }
    }

    private void rememberApplied(Path file) {
        try {
            Files.createDirectories(file.resolveSibling(APPLIED_FOLDER));
            Files.writeString(appliedRecord(file), fingerprint(file));
        } catch (IOException e) {
            plugin().getLogger().log(Level.WARNING, "Could not note the synced " + file.getFileName() + ".", e);
        }
    }

    private Path appliedRecord(Path file) {
        return file.resolveSibling(APPLIED_FOLDER).resolve(file.getFileName() + ".sha256");
    }

    private static String fingerprint(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    /**
     * Keeps a copy of a file before the primary's copy replaces it, so a change made on this node is not lost.
     *
     * @param file the file
     * @return whether the backup was written
     */
    private boolean keepBackup(Path file) {
        var stamp = BACKUP_TIME.format(Times.local(Times.now()));
        var backup = file.resolveSibling("backup_" + stamp + "_" + file.getFileName());
        for (var attempt = 2; Files.exists(backup); attempt++) {
            backup = file.resolveSibling("backup_" + stamp + "-" + attempt + "_" + file.getFileName());
        }
        try {
            Files.copy(file, backup);
            plugin().getLogger()
                    .warning(file.getFileName() + " was changed on this server and differs from the primary's copy,"
                            + " which replaces it now. This server's version is kept as " + backup.getFileName()
                            + ".");
            return true;
        } catch (IOException e) {
            plugin().getLogger()
                    .log(
                            Level.SEVERE,
                            "Could not keep a backup of " + file.getFileName()
                                    + ", so the primary's copy does not replace it.",
                            e);
            return false;
        }
    }

    private JsonNode tree(Path file) {
        try {
            return Files.exists(file) ? YAML.readTree(Files.readString(file)) : MissingNode.getInstance();
        } catch (IOException | JacksonException e) {
            return MissingNode.getInstance();
        }
    }

    private JsonNode tree(String content) {
        try {
            return YAML.readTree(content);
        } catch (JacksonException e) {
            return MissingNode.getInstance();
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
