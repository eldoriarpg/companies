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
import de.eldoria.companies.data.wrapper.nodes.SettingsRevision;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.config.JacksonConfig;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
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

    /**
     * The files every node of a network shares, in the order they are synced.
     */
    public static final List<Key<?>> SHARED = List.of(COMPANY_SETTINGS, USER_SETTINGS, ORDER_SETTINGS, CONFIG_YML);

    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final YAMLMapper YAML = YAMLMapper.builder().build();
    private static final String APPLIED_FOLDER = ".synced";
    private static final String ACTOR_SYNC = "sync";

    private final Set<Key<?>> unreadable = ConcurrentHashMap.newKeySet();
    private final Object copying = new Object();
    private final Set<Path> backupFailed = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> applied = new ConcurrentHashMap<>();
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
     * Writes a file into the database on the primary, or takes the primary's copy on a secondary, saves it and reads it
     * again, which applies the environment overrides of this node. A value set through an environment variable is part
     * of neither copy.
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

    /**
     * A shared file this node changed against the revision it last applied.
     *
     * @param key     the file
     * @param base    the revision the change started from
     * @param content the changed content, as {@link ANodeData#primaryCopy} writes it
     * @param changes the changed keys as dotted paths, such as {@code economy.fee.percent}
     */
    public record Edit(Key<?> key, int base, String content, List<String> changes) {
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
     */
    public record EditOutcome(
            String path, OptionalInt revision, @Nullable String conflictFile) {}

    /**
     * The shared files this node changed against the revisions it last applied, as read now. Unreadable files and files
     * without a stored revision yet are left out.
     *
     * @param nodeData the node repository holding the revisions
     * @return the edits, in the order the files are synced
     */
    public List<Edit> edits(ANodeData nodeData) {
        var edits = new ArrayList<Edit>();
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
            edits.add(new Edit(key, base.getAsInt(), content, changes(before, after)));
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

    private EditOutcome publishEdit(ANodeData nodeData, Key<?> key, int base, String content, String actor) {
        var path = key.path().toString();
        return switch (nodeData.publishSettingsOn(path, base, content, actor)) {
            case ANodeData.PublishOutcome.Published published -> {
                if (primary()) nodeData.savePrimaryCopy(key, content);
                stored(key, published.revision());
                yield new EditOutcome(path, OptionalInt.of(published.revision()), null);
            }
            case ANodeData.PublishOutcome.Unchanged unchanged -> {
                noteApplied(nodeData, path, unchanged.revision());
                yield new EditOutcome(path, OptionalInt.of(unchanged.revision()), null);
            }
            case ANodeData.PublishOutcome.Conflict conflict ->
                new EditOutcome(path, OptionalInt.empty(), conflict(nodeData, key, conflict.latest(), content));
        };
    }

    /**
     * Keeps this node's version of a file another server changed meanwhile and takes the newer revision.
     *
     * @param content this node's version, kept as {@code conflict_<revision>_<file>}
     * @return the name of the kept file, none when it could not be kept and the file stays as it is
     */
    private @Nullable String conflict(ANodeData nodeData, Key<?> key, int latest, String content) {
        var file = plugin().getDataFolder().toPath().resolve(key.path());
        var kept = keepContent(file, "conflict_" + latest, content);
        if (kept == null) return null;
        plugin().getLogger()
                .warning(file.getFileName() + " was changed on this server and on another one (revision " + latest
                        + "). This server's version is kept as " + kept + " and revision " + latest + " is used.");
        take(nodeData, key, false);
        return kept;
    }

    private boolean primary() {
        return nodeSettings().nodeType() == NodeType.PRIMARY;
    }

    private void stored(Key<?> key, int revision) {
        var path = key.path().toString();
        plugin().getLogger().info("Stored " + path + " as revision " + revision + ".");
        applied.put(path, revision);
        rememberApplied(plugin().getDataFolder().toPath().resolve(key.path()));
        var announce = announcer;
        if (announce != null) announce.accept(path, revision);
    }

    /**
     * @param before a file's content as it was
     * @param after  the file's content now
     * @return the keys that differ, as dotted paths, sorted
     */
    static List<String> changes(JsonNode before, JsonNode after) {
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
        var path = configKey.path().toString();
        nodeData.savePrimaryCopy(configKey, content);
        var revision = nodeData.publishSettings(path, content, actor);
        if (revision.isEmpty()) {
            nodeData.latestSettings(path).ifPresent(latest -> noteApplied(nodeData, path, latest.revision()));
            return;
        }
        stored(configKey, revision.getAsInt());
    }

    /**
     * Takes a shared file's revision on a secondary, unless this node applied it or a later one already.
     *
     * @param nodeData the node repository holding the revisions
     * @param path     the file
     * @param revision the revision announced or seen
     * @return whether the file changed
     */
    public boolean takeIfNewer(ANodeData nodeData, String path, int revision) {
        var key = sharedKey(path);
        if (key.isEmpty() || applied.getOrDefault(path, 0) >= revision) return false;
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
     * Takes the primary's copy of a shared file on a secondary: saves it, reads it again, which applies this node's
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
                return false;
            }
            if (backupEdits && Files.exists(file) && editedHere(file) && !keepBackup(file)) return false;
            plugin().getLogger()
                    .info("Taking " + path + (revision > 0 ? " revision " + revision : "") + " from the database.");
            replace(configKey, nodeData.readSettings(configKey, this, copy));
            save(configKey);
            reload(configKey);
            rememberApplied(file);
            if (revision > 0) noteApplied(nodeData, path, revision);
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
     * Keeps a copy of a file before the primary's copy replaces it, so a change made on this node is not lost. A backup
     * that keeps failing is reported once, until one is written again.
     *
     * @param file the file
     * @return whether the backup was written
     */
    private boolean keepBackup(Path file) {
        var kept = keepCopy(file, "backup_" + BACKUP_TIME.format(Times.local(Times.now())));
        if (kept == null) return false;
        plugin().getLogger()
                .warning(file.getFileName() + " was changed on this server and differs from the primary's copy, which"
                        + " replaces it now. This server's version is kept as " + kept + ".");
        return true;
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
