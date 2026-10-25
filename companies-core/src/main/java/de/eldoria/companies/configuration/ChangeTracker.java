/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Tells which settings of the shared files changed: since they were last applied, so only the work that depends on
 * them runs, and since the server started, for the settings that are only read at start.
 * <p>
 * Each file is kept as the tree its content serializes to. Until {@link #start()} ran, nothing counts as changed.
 */
public final class ChangeTracker {
    /**
     * The settings that are only read when the server starts.
     */
    public static final List<Setting> RESTART = List.of(
            Setting.of("config.yml", "generalSettings.orderCheckInterval"),
            Setting.of("config.yml", "generalSettings.messageBlocking"),
            Setting.of("config.yml", "generalSettings.hooks"),
            Setting.of("config.yml", "placeholders.offlineCacheSize"),
            Setting.of("config.yml", "boards.enabled"),
            Setting.of("order.yml", "repeating.checkMinutes"));

    private final List<String> paths;
    private final Function<String, JsonNode> current;
    private final Map<String, JsonNode> applied = new HashMap<>();
    private final Map<String, JsonNode> started = new HashMap<>();

    /**
     * @param paths   the shared files
     * @param current the tree of a file's current content, by its path
     */
    public ChangeTracker(List<String> paths, Function<String, JsonNode> current) {
        this.paths = List.copyOf(paths);
        this.current = current;
    }

    /**
     * Takes the files as they are now as both started and applied.
     */
    public synchronized void start() {
        for (var path : paths) {
            var tree = current.apply(path);
            started.put(path, tree);
            applied.put(path, tree);
        }
    }

    /**
     * Takes the files as they are now as applied.
     *
     * @return the settings that changed since they were last applied, as dotted keys per file
     */
    public synchronized Set<Setting> changes() {
        if (started.isEmpty()) return Set.of();
        var found = new LinkedHashSet<Setting>();
        for (var path : paths) {
            var tree = current.apply(path);
            Configuration.changes(applied.get(path), tree).forEach(key -> found.add(new Setting(path, key)));
            applied.put(path, tree);
        }
        return found;
    }

    /**
     * @return the settings read only at start that differ from what the server started with
     */
    public synchronized List<Setting> restartPending() {
        if (started.isEmpty()) return List.of();
        var found = new LinkedHashSet<Setting>();
        for (var path : paths) {
            var tree = current.apply(path);
            for (var key : Configuration.changes(started.get(path), tree)) {
                RESTART.stream().filter(setting -> setting.covers(path, key)).forEach(found::add);
            }
        }
        return List.copyOf(found);
    }

    /**
     * @param settings the settings work depends on, none when it depends on every setting
     * @param changes  the settings that changed
     * @return whether a change touches the work
     */
    public static boolean touches(List<Setting> settings, Set<Setting> changes) {
        if (changes.isEmpty()) return false;
        return settings.isEmpty()
                || changes.stream().anyMatch(change -> settings.stream().anyMatch(setting -> setting.covers(change)));
    }

    /**
     * A setting, or a section of settings, of one shared file.
     *
     * @param path the file, such as {@code config.yml}
     * @param key  the dotted key, such as {@code discord} or {@code discord.url}
     */
    public record Setting(String path, String key) {
        /**
         * @param path the file
         * @param key  the dotted key
         * @return the setting
         */
        public static Setting of(String path, String key) {
            return new Setting(path, key);
        }

        /**
         * @param path       a file
         * @param changedKey a dotted key that changed in it
         * @return whether that key is this setting, lies in it or holds it
         */
        public boolean covers(String path, String changedKey) {
            return this.path.equals(path)
                    && (changedKey.isEmpty()
                            || changedKey.equals(key)
                            || changedKey.startsWith(key + ".")
                            || key.startsWith(changedKey + "."));
        }

        /**
         * @param changed a setting that changed
         * @return whether it is this setting, lies in it or holds it
         */
        public boolean covers(Setting changed) {
            return covers(changed.path(), changed.key());
        }

        @Override
        public String toString() {
            return path + " " + key;
        }
    }
}
