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
import de.eldoria.companies.configuration.elements.dashboard.BrandingSettings;
import de.eldoria.companies.configuration.elements.generalsettings.Hooks;
import de.eldoria.companies.configuration.elements.systemorders.SystemOrderSettings;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.wrapper.nodes.AppliedSettings;
import de.eldoria.companies.data.wrapper.nodes.SettingsRevision;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.config.JacksonConfig;
import dev.chojo.ocular.components.Format;
import dev.chojo.ocular.dataformats.YamlDataFormat;
import dev.chojo.ocular.exceptions.ConfigurationException;
import dev.chojo.ocular.key.Key;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;
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
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
    public static final Key<SystemOrderSettings> SYSTEM_ORDERS = Key.builder(
                    Path.of("system-orders.yml"), SystemOrderSettings::new)
            .name("System orders")
            .strict()
            .build();
    public static final Key<BoardBindings> BOARD_BINDINGS = Key.builder(Path.of("boards.yml"), BoardBindings::new)
            .name("Board bindings")
            .build();

    /**
     * The files every node of a network shares, in the order they are synced.
     */
    public static final List<Key<?>> SHARED =
            List.of(COMPANY_SETTINGS, USER_SETTINGS, ORDER_SETTINGS, SYSTEM_ORDERS, CONFIG_YML);

    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final YAMLMapper YAML = YAMLMapper.builder().build();
    private static final String APPLIED_FOLDER = ".synced";
    private static final String ACTOR_SYNC = "sync";

    private final Set<Key<?>> unreadable = ConcurrentHashMap.newKeySet();
    private final Object copying = new Object();
    private final Set<Path> backupFailed = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> applied = new ConcurrentHashMap<>();
    private final ChangeTracker tracker = new ChangeTracker(
            SHARED.stream().map(key -> key.path().toString()).toList(),
            this::currentTree,
            this::primary,
            this::restartValues);
    private volatile @Nullable ObjectMapper writer;
    private volatile @Nullable BiConsumer<String, Integer> announcer;

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
     * Whether a file the network shares ({@code config.yml}, {@code company.yml}, {@code user.yml}, {@code order.yml},
     * {@code system-orders.yml})
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
        if (value instanceof NodeSettings node) {
            node.overrides().problems().forEach(problem -> plugin().getLogger()
                    .warning(key.path() + ": " + problem + ". It is ignored and the shared value is used."));
        }
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

    /**
     * @return the orders the server places itself
     */
    public SystemOrderSettings systemOrderSettings() {
        return secondary(SYSTEM_ORDERS);
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
        return syncConfigurations(nodeData, ACTOR_SYNC);
    }

    /**
     * Stores the shared files as their next revisions on the primary, each only when it changed, or takes the latest
     * revisions on a secondary.
     *
     * @param nodeData the node repository holding the revisions
     * @param actor    who caused the sync, stored with a new revision, such as an admin's name
     * @return whether a secondary's files changed, false on the primary and when every file matched
     */
    public CompletableFuture<Boolean> syncConfigurations(ANodeData nodeData, String actor) {
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
            var changed = false;
            for (var key : SHARED) changed |= sync(nodeData, key, actor);
            return changed;
        });
    }

    /**
     * Stores a file as a revision on the primary, checked against its base, or takes the latest revision on a
     * secondary, saving it and reading it again, which applies the environment overrides of this node. A value set
     * through an environment variable is part of no revision.
     * <p>
     * The primary compares and stores under the copy lock, so a rollback or edit on this node that is stored and not yet
     * taken is never read as its base.
     *
     * @param nodeData  the node repository holding the copies
     * @param configKey the file
     * @param <V>       type of the file's content
     */
    private <V> boolean sync(ANodeData nodeData, Key<V> configKey, String actor) {
        if (!primary()) return takePrimaryCopy(nodeData, configKey);
        secondary(configKey);
        if (unreadable(configKey)) {
            plugin().getLogger().warning("Not syncing " + configKey.path() + " to database, since it cannot be read.");
            return false;
        }
        synchronized (copying) {
            var path = configKey.path().toString();
            var content = nodeData.primaryCopy(configKey, this);
            var latest = nodeData.latestSettings(path);
            var base = nodeData.appliedRevision(path);
            if (latest.isEmpty() || base.isEmpty()) {
                publish(nodeData, configKey, content, actor);
                return false;
            }
            if (latest.get().revision() == base.getAsInt()) {
                publishEdit(nodeData, configKey, base.getAsInt(), content, actor);
                return false;
            }
            var unchangedHere = nodeData.settingsAt(path, base.getAsInt())
                    .map(stored -> tree(stored.content()).equals(tree(content)))
                    .orElse(false);
            if (unchangedHere) return takePrimaryCopy(nodeData, configKey);
            conflict(nodeData, configKey, latest.get().revision(), content);
            return true;
        }
    }

    /**
     * A shared file this node changed against the revision it last applied.
     *
     * @param key     the file
     * @param base    the revision the change started from
     * @param content the changed content, as {@link ANodeData#primaryCopy} writes it
     * @param changes the changed keys as dotted paths, such as {@code economy.fee.percent}
     * @param behind  whether another server stored a newer revision already, so storing the edit will conflict
     */
    public record Edit(Key<?> key, int base, String content, List<String> changes, boolean behind) {
        /**
         * @return the file's path
         */
        public String path() {
            return key.path().toString();
        }
    }

    /**
     * What storing an edit did.
     *
     * @param path         the file
     * @param revision     the revision it became, empty when it was not stored
     * @param conflictFile where this node's version was kept when another server changed the file meanwhile
     * @param replaced     whether the newer revision replaced this node's version, so it has to be applied
     */
    public record EditOutcome(
            String path, OptionalInt revision, @Nullable String conflictFile, boolean replaced) {}

    /**
     * The shared files this node changed against the revisions it last applied, as read now. Unreadable files and files
     * without a stored revision yet are left out.
     *
     * @param nodeData the node repository holding the revisions
     * @return the edits, in the order the files are synced
     */
    public List<Edit> edits(ANodeData nodeData) {
        var edits = new ArrayList<Edit>();
        var revisions = nodeData.settingsRevisions();
        for (var key : SHARED) {
            if (unreadable(key)) continue;
            var path = key.path().toString();
            var base = applied.containsKey(path) ? OptionalInt.of(applied.get(path)) : nodeData.appliedRevision(path);
            if (base.isEmpty()) continue;
            var stored = nodeData.settingsAt(path, base.getAsInt());
            if (stored.isEmpty()) continue;
            var content = nodeData.primaryCopy(key, this);
            var before = tree(stored.get().content());
            var after = tree(content);
            if (before.equals(after)) continue;
            var latest = revisions.getOrDefault(path, base.getAsInt());
            edits.add(new Edit(key, base.getAsInt(), content, changedKeys(before, after), latest > base.getAsInt()));
        }
        return edits;
    }

    /**
     * Stores edits as new revisions on top of their bases. An edit whose file was changed on another server meanwhile is
     * not stored: this node's version is kept as {@code conflict_<revision>_<file>} and the newer revision is taken.
     *
     * @param nodeData the node repository holding the revisions
     * @param edits    the edits
     * @param actor    who made them
     * @return what happened to each
     */
    public List<EditOutcome> publishEdits(ANodeData nodeData, List<Edit> edits, String actor) {
        var outcomes = new ArrayList<EditOutcome>();
        for (var edit : edits) {
            outcomes.add(publishEdit(nodeData, edit.key(), edit.base(), edit.content(), actor));
        }
        return outcomes;
    }

    /**
     * Stores one edit as the revision after its base, as {@link #publishEdits} does for each. Level changes go through
     * here too, so they never replace a revision another server stored meanwhile.
     *
     * @param nodeData the node repository holding the revisions
     * @param key      the file
     * @param base     the revision the edit started from
     * @param content  the edited content
     * @param actor    who made it
     * @return what happened
     */
    public EditOutcome publishEdit(ANodeData nodeData, Key<?> key, int base, String content, String actor) {
        synchronized (copying) {
            var path = key.path().toString();
            return switch (nodeData.publishSettingsOn(path, base, content, actor)) {
                case ANodeData.PublishOutcome.Published published -> {
                    if (primary()) nodeData.savePrimaryCopy(key, content);
                    stored(key, published.revision(), content);
                    yield new EditOutcome(path, OptionalInt.of(published.revision()), null, false);
                }
                case ANodeData.PublishOutcome.Unchanged unchanged -> {
                    if (primary()) nodeData.savePrimaryCopy(key, content);
                    noteApplied(nodeData, path, unchanged.revision());
                    rememberIfMatches(key, content);
                    yield new EditOutcome(path, OptionalInt.of(unchanged.revision()), null, false);
                }
                case ANodeData.PublishOutcome.Conflict conflict -> {
                    var kept = conflict(nodeData, key, conflict.latest(), content);
                    yield kept == null
                            ? new EditOutcome(path, OptionalInt.empty(), null, false)
                            : new EditOutcome(path, OptionalInt.empty(), kept.file(), kept.replaced());
                }
            };
        }
    }

    /**
     * Makes an earlier revision of a shared file the newest again: stores its content as the revision after
     * {@code base} and applies it on this node. The other nodes take it as any new revision. When a revision newer than
     * {@code base} was stored meanwhile, nothing is stored, so a rollback never silently replaces a change it was not
     * asked about.
     * <p>
     * When this node cannot take it, because a file changed here could not be backed up, its applied revision is set
     * back, so it counts as behind and a later sync does not store its own file over the rollback.
     *
     * @param nodeData the node repository holding the revisions
     * @param path     the file
     * @param revision the earlier revision
     * @param base     the latest revision when the rollback was asked for
     * @param actor    who rolled back
     * @return what happened, empty when the file is not shared or has no such revision
     */
    public Optional<StoreOutcome> rollback(ANodeData nodeData, String path, int revision, int base, String actor) {
        var earlier = nodeData.settingsAt(path, revision);
        if (earlier.isEmpty()) return Optional.empty();
        return store(nodeData, path, base, earlier.get().content(), actor);
    }

    /**
     * Stores a shared file's content as the revision after {@code base} and applies it on this node; the other nodes
     * take it as any new revision. When a revision newer than {@code base} was stored meanwhile, nothing is stored and
     * no conflict file is kept, since the caller still holds the content. When this node cannot take it, because a file
     * changed here could not be backed up, its applied revision is set back, so it counts as behind.
     *
     * @param nodeData the node repository holding the revisions
     * @param path     the file
     * @param base     the revision the content was made on
     * @param content  the file's content
     * @param actor    who stored it
     * @return what happened, empty when the file is not shared
     */
    public Optional<StoreOutcome> store(ANodeData nodeData, String path, int base, String content, String actor) {
        var key = sharedKey(path);
        if (key.isEmpty()) return Optional.empty();
        synchronized (copying) {
            return Optional.of(
                    switch (nodeData.publishSettingsOn(path, base, content, actor)) {
                        case ANodeData.PublishOutcome.Published published -> {
                            plugin().getLogger()
                                    .info("Stored " + path + " as revision " + published.revision() + " for " + actor
                                            + ".");
                            var announce = announcer;
                            if (announce != null) announce.accept(path, published.revision());
                            var replaced = take(nodeData, key.get(), true);
                            var applied = appliedRevision(path) >= published.revision();
                            if (!applied) nodeData.appliedSettings(path, appliedRevision(path));
                            yield new StoreOutcome.Stored(published.revision(), replaced, applied);
                        }
                        case ANodeData.PublishOutcome.Unchanged unchanged ->
                            new StoreOutcome.Unchanged(unchanged.revision());
                        case ANodeData.PublishOutcome.Conflict conflict -> new StoreOutcome.Newer(conflict.latest());
                    });
        }
    }

    /**
     * What storing a file's content as a new revision did.
     */
    public sealed interface StoreOutcome {
        /**
         * The content was stored as a new revision.
         *
         * @param revision the new revision
         * @param replaced whether this node's file changed
         * @param applied  whether this node uses the new revision; not when a file changed here could not be backed up
         */
        record Stored(int revision, boolean replaced, boolean applied) implements StoreOutcome {}

        /**
         * The content equals the latest revision, so nothing was stored.
         *
         * @param latest the latest revision
         */
        record Unchanged(int latest) implements StoreOutcome {}

        /**
         * A revision newer than the base was stored meanwhile, so nothing was stored.
         *
         * @param latest the newer revision
         */
        record Newer(int latest) implements StoreOutcome {}
    }

    /**
     * How one shared file stands across the network.
     *
     * @param path    the file
     * @param latest  its latest revision, 0 before one was stored
     * @param applied the revision this node applied, 0 before it applied one
     * @param edited  whether this node changed it without sharing the change
     * @param behind  the nodes that applied an older revision than the latest
     */
    public record FileStatus(String path, int latest, int applied, boolean edited, List<UUID> behind) {}

    /**
     * @param nodeData the node repository holding the revisions
     * @return how each shared file stands, in the order they are synced
     */
    public List<FileStatus> status(ANodeData nodeData) {
        var revisions = nodeData.settingsRevisions();
        var appliedRows = nodeData.settingsApplied();
        var edited = edits(nodeData).stream().map(Edit::path).collect(Collectors.toSet());
        var statuses = new ArrayList<FileStatus>();
        for (var key : SHARED) {
            var path = key.path().toString();
            var latest = revisions.getOrDefault(path, 0);
            var behind = appliedRows.stream()
                    .filter(row -> row.path().equals(path) && row.revision() < latest)
                    .map(AppliedSettings::node)
                    .toList();
            statuses.add(new FileStatus(path, latest, appliedRevision(path), edited.contains(path), behind));
        }
        return statuses;
    }

    /**
     * @return whether chat is held back while a chat menu is open on this server
     */
    public boolean messageBlocking() {
        return nodeSettings().overrides().messageBlocking(generalSettings().messageBlocking());
    }

    /**
     * @return whether order boards are on on this server
     */
    public boolean boardsEnabled() {
        return nodeSettings().overrides().boardsEnabled(boardSettings().enabled());
    }

    /**
     * @return the plugin hook switches of this server
     */
    public Hooks hooks() {
        return nodeSettings().overrides().hooks(generalSettings().hooks());
    }

    /**
     * @return the dashboard name and icon of this server
     */
    public BrandingSettings branding() {
        return nodeSettings().overrides().branding(dashboardSettings().branding());
    }

    /**
     * @return the value of each setting read only at start, as this server uses it
     */
    private Map<ChangeTracker.Setting, Object> restartValues() {
        var general = generalSettings();
        var hooks = hooks();
        return Map.of(
                ChangeTracker.ORDER_CHECK, general.orderCheckInterval(),
                ChangeTracker.MESSAGE_BLOCKING, messageBlocking(),
                ChangeTracker.HOOKS, List.of(hooks.placeholderapi(), hooks.citizens(), hooks.fancynpcs()),
                ChangeTracker.OFFLINE_CACHE, placeholderSettings().offlineCacheSize(),
                ChangeTracker.BOARDS_ENABLED, boardsEnabled(),
                ChangeTracker.REPEATING_CHECK, orderSetting().repeating().checkMinutes(),
                ChangeTracker.SYSTEM_ORDER_CHECK, systemOrderSettings().checkMinutes());
    }

    /**
     * Takes the shared files as they are now as the ones this server started with, once the start sync is done.
     */
    public void started() {
        tracker.start();
    }

    /**
     * @return the settings of the shared files that changed since they were last applied; they count as applied now
     */
    public Set<ChangeTracker.Setting> changes() {
        return tracker.changes();
    }

    /**
     * @return the settings read only at start that changed since this server started
     */
    public List<ChangeTracker.Setting> restartPending() {
        return tracker.restartPending();
    }

    /**
     * @param path a shared file
     * @return the tree its current content serializes to, empty when it cannot be written now, for example while it
     *     is being changed
     */
    public Optional<JsonNode> currentTree(String path) {
        var key = sharedKey(path).orElseThrow();
        try {
            return Optional.of(YAML.readTree(writer().writeValueAsString(secondary(key))));
        } catch (RuntimeException e) {
            plugin().getLogger().log(Level.FINE, "Could not compare " + path + " now.", e);
            return Optional.empty();
        }
    }

    private ObjectMapper writer() {
        var current = writer;
        if (current == null) {
            current = new Format<>(new YamlDataFormat(), this).writer();
            writer = current;
        }
        return current;
    }

    /**
     * @param path a shared file
     * @return the revision this node applied of it, 0 before it applied one
     */
    public int appliedRevision(String path) {
        return applied.getOrDefault(path, 0);
    }

    /**
     * Keeps this node's version of a file another server changed meanwhile and takes the newer revision. A file on disk
     * that holds neither this version nor the newer revision, changed again after the reload, is backed up first.
     *
     * @param content this node's version, kept as {@code conflict_<revision>_<file>}
     * @return where it was kept and whether the newer revision replaced the file, none when nothing could be kept and
     *     the file stays as it is
     */
    private @Nullable Kept conflict(ANodeData nodeData, Key<?> key, int latest, String content) {
        var file = plugin().getDataFolder().toPath().resolve(key.path());
        var newer = nodeData.settingsAt(key.path().toString(), latest)
                .map(stored -> tree(stored.content()))
                .orElse(MissingNode.getInstance());
        var onDisk = tree(file);
        if (Files.exists(file) && !onDisk.equals(tree(content)) && !onDisk.equals(newer)) {
            if (keepBackup(file, latest) == null) return null;
        }
        var kept = keepContent(file, "conflict_" + latest, content);
        if (kept == null) return null;
        plugin().getLogger()
                .warning(file.getFileName() + " was changed on this server and on another one (revision " + latest
                        + "). This server's version is kept as " + kept + " and revision " + latest + " is used.");
        return new Kept(kept, take(nodeData, key, false));
    }

    /**
     * Where a conflicting version was kept.
     *
     * @param file     the kept file's name
     * @param replaced whether taking the newer revision changed the file
     */
    private record Kept(String file, boolean replaced) {}

    private boolean primary() {
        return nodeSettings().nodeType() == NodeType.PRIMARY;
    }

    private void stored(Key<?> key, int revision, String content) {
        var path = key.path().toString();
        plugin().getLogger().info("Stored " + path + " as revision " + revision + ".");
        applied.put(path, revision);
        rememberIfMatches(key, content);
        var announce = announcer;
        if (announce != null) announce.accept(path, revision);
    }

    /**
     * Notes the file as taken when it holds what was stored, so a later revision does not count it as edited here. A
     * file changed again meanwhile is left as an edit.
     */
    private void rememberIfMatches(Key<?> key, String content) {
        var file = plugin().getDataFolder().toPath().resolve(key.path());
        if (tree(file).equals(tree(content))) rememberApplied(file);
    }

    /**
     * @param before a file's content as it was
     * @param after  the file's content now
     * @return the keys that differ, as dotted paths, sorted
     */
    public static List<String> changedKeys(JsonNode before, JsonNode after) {
        var found = new TreeSet<String>();
        diff("", before, after, found);
        return List.copyOf(found);
    }

    private static void diff(String prefix, JsonNode before, JsonNode after, Set<String> found) {
        if (before.isObject() && after.isObject()) {
            var names = new TreeSet<String>();
            before.properties().forEach(entry -> names.add(entry.getKey()));
            after.properties().forEach(entry -> names.add(entry.getKey()));
            for (var name : names) {
                diff(prefix.isEmpty() ? name : prefix + "." + name, before.path(name), after.path(name), found);
            }
            return;
        }
        if (!before.equals(after)) found.add(prefix);
    }

    /**
     * Stores a shared file's content as its next revision when it changed, keeps the older single copy for a
     * downgrade, and announces a new revision to the other nodes.
     *
     * @param nodeData  the node repository holding the revisions
     * @param configKey the file
     * @param content   its content, as {@link ANodeData#primaryCopy} wrote it
     * @param actor     who changed it
     */
    public void publish(ANodeData nodeData, Key<?> configKey, String content, String actor) {
        synchronized (copying) {
            var path = configKey.path().toString();
            nodeData.savePrimaryCopy(configKey, content);
            var revision = nodeData.publishSettings(path, content, actor);
            if (revision.isEmpty()) {
                nodeData.latestSettings(path).ifPresent(latest -> noteApplied(nodeData, path, latest.revision()));
                rememberIfMatches(configKey, content);
                return;
            }
            stored(configKey, revision.getAsInt(), content);
        }
    }

    /**
     * Takes a shared file's revision another node stored, unless this node applied it or a later one already. A primary
     * takes nothing over a file it cannot read: that file stays as it is on disk until a reload reads it, also when
     * the primary hears its own announcement of the revision.
     *
     * @param nodeData the node repository holding the revisions
     * @param path     the file
     * @param revision the revision announced or seen
     * @return whether the file changed
     */
    public boolean takeIfNewer(ANodeData nodeData, String path, int revision) {
        var key = sharedKey(path);
        if (key.isEmpty() || applied.getOrDefault(path, 0) >= revision) return false;
        if (primary() && unreadable(key.get())) return false;
        return takePrimaryCopy(nodeData, key.get());
    }

    /**
     * @param path a file's path, such as {@code company.yml}
     * @return the shared file of that path
     */
    public Optional<Key<?>> sharedKey(String path) {
        return SHARED.stream()
                .filter(key -> key.path().toString().equals(path))
                .findFirst()
                .map(key -> key);
    }

    /**
     * Tells the other nodes about every new revision this node stores, from now on.
     *
     * @param announcer gets the file's path and its new revision
     */
    public void announceWith(BiConsumer<String, Integer> announcer) {
        this.announcer = announcer;
    }

    /**
     * @return the revision this node applied of each shared file, by path
     */
    public Map<String, Integer> appliedRevisions() {
        return Map.copyOf(applied);
    }

    private void noteApplied(ANodeData nodeData, String path, int revision) {
        applied.put(path, revision);
        nodeData.appliedSettings(path, revision);
    }

    /**
     * Takes the latest stored revision of a shared file: saves it, reads it again, which applies this node's
     * environment overrides, and remembers what was written. A file that matches the copy is left alone. A file that
     * changed on this node since it last took a copy, or one never taken, is kept as
     * {@code backup_<time>_<file>} first; when that backup cannot be written, the file stays and nothing is taken. One
     * copy is taken at a time, so a full sync and a level change do not write a file at once. The latest stored
     * revision is taken; before the primary stored one, the older single copy is.
     *
     * @param nodeData  the node repository holding the copies
     * @param configKey the file
     * @param <V>       type of the file's content
     * @return whether the file changed
     */
    public <V> boolean takePrimaryCopy(ANodeData nodeData, Key<V> configKey) {
        return take(nodeData, configKey, true);
    }

    private <V> boolean take(ANodeData nodeData, Key<V> configKey, boolean backupEdits) {
        synchronized (copying) {
            var path = configKey.path().toString();
            var latest = nodeData.latestSettings(path);
            var copy = latest.map(SettingsRevision::content)
                    .or(() -> nodeData.primaryContent(configKey))
                    .orElseThrow(() -> new IllegalStateException(
                            "No configuration present. Did you start the main node once already?"));
            var revision = latest.map(SettingsRevision::revision).orElse(0);
            var file = plugin().getDataFolder().toPath().resolve(configKey.path());
            if (tree(copy).equals(tree(file))) {
                rememberApplied(file);
                if (revision > 0) noteApplied(nodeData, path, revision);
                if (primary() && revision > 0) nodeData.savePrimaryCopy(configKey, copy);
                return false;
            }
            if (backupEdits && Files.exists(file) && editedHere(file) && keepBackup(file, revision) == null) {
                return false;
            }
            plugin().getLogger()
                    .info("Taking " + path + (revision > 0 ? " revision " + revision : "") + " from the database.");
            replace(configKey, nodeData.readSettings(configKey, this, copy));
            save(configKey);
            reload(configKey);
            rememberApplied(file);
            if (revision > 0) noteApplied(nodeData, path, revision);
            if (primary()) nodeData.savePrimaryCopy(configKey, copy);
            return true;
        }
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
     * Keeps a copy of a file before a newer revision replaces it, so a change made on this node is not lost. A backup
     * that keeps failing is reported once, until one is written again.
     *
     * @param file the file
     * @return whether the backup was written
     */
    private @Nullable String keepBackup(Path file, int revision) {
        var kept = keepCopy(file, "backup_" + BACKUP_TIME.format(Times.local(Times.now())));
        if (kept == null) return null;
        plugin().getLogger()
                .warning(file.getFileName() + " was changed on this server and differs from revision " + revision
                        + ", which replaces it now. This server's version is kept as " + kept + ".");
        return kept;
    }

    /**
     * Copies a file next to it as {@code <prefix>_<file name>}, with a number added when that name is taken. A copy
     * that keeps failing is reported once, until one is written again.
     *
     * @param file   the file
     * @param prefix start of the copy's name
     * @return the copy's name, none when it could not be written
     */
    private @Nullable String keepCopy(Path file, String prefix) {
        return keep(file, prefix, copy -> Files.copy(file, copy));
    }

    private @Nullable String keepContent(Path file, String prefix, String content) {
        return keep(file, prefix, copy -> Files.writeString(copy, content, StandardOpenOption.CREATE_NEW));
    }

    private @Nullable String keep(Path file, String prefix, Writer writer) {
        var name = String.valueOf(file.getFileName());
        var kept = prefix + "_" + name;
        for (var attempt = 2; Files.exists(file.resolveSibling(kept)); attempt++) {
            kept = prefix + "-" + attempt + "_" + name;
        }
        try {
            writer.write(file.resolveSibling(kept));
            backupFailed.remove(file);
            return kept;
        } catch (IOException e) {
            plugin().getLogger()
                    .log(
                            backupFailed.add(file) ? Level.SEVERE : Level.FINE,
                            "Could not keep a copy of " + name + ", so it is not replaced.",
                            e);
            return null;
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

    /**
     * Writes the kept version of a file.
     */
    @FunctionalInterface
    private interface Writer {
        /**
         * @param target where the kept version goes
         * @throws IOException when it cannot be written
         */
        void write(Path target) throws IOException;
    }
}
