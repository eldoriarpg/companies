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
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Tells which settings of the shared files changed: since they were last applied, so only the work that depends on
 * them runs, and since the server started, for the settings that are only read at start.
 * <p>
 * Each file is kept as the tree its content serializes to. Until {@link #start()} ran, nothing counts as changed.
 */
public final class ChangeTracker {
    private static final String CONFIG = "config.yml";

    /** The time zone, warned about when unknown. */
    public static final Setting TIMEZONE = Setting.of(CONFIG, "generalSettings.timezone");
    /** The update checks. */
    public static final Setting UPDATES = Setting.of(CONFIG, "generalSettings.updates");
    /** How notifications are polled. */
    public static final Setting NOTIFICATIONS = Setting.of(CONFIG, "notifications");
    /** The Discord webhook. */
    public static final Setting DISCORD = Setting.of(CONFIG, "discord");
    /** The dashboard connection. */
    public static final Setting DASHBOARD = Setting.of(CONFIG, "dashboard");
    /** The ranking caches. */
    public static final Setting TOP = Setting.of(CONFIG, "top");

    /**
     * The settings reload work depends on.
     */
    public static final List<Setting> HOOKED = List.of(TIMEZONE, UPDATES, NOTIFICATIONS, DISCORD, DASHBOARD, TOP);

    /**
     * The settings that are only read when the server starts.
     */
    public static final List<Setting> RESTART = List.of(
            Setting.of(CONFIG, "generalSettings.orderCheckInterval"),
            Setting.of(CONFIG, "generalSettings.messageBlocking"),
            Setting.of(CONFIG, "generalSettings.hooks"),
            Setting.of(CONFIG, "placeholders.offlineCacheSize"),
            Setting.of(CONFIG, "boards.enabled"),
            Setting.of("order.yml", "repeating.checkMinutes"));

    /**
     * The settings of {@link #RESTART} only the primary uses, so a secondary needs no restart for them.
     */
    public static final List<Setting> PRIMARY_ONLY = List.of(RESTART.getFirst(), RESTART.getLast());

    private final List<String> paths;
    private final Function<String, Optional<JsonNode>> current;
    private final BooleanSupplier primary;
    private final Map<String, JsonNode> applied = new HashMap<>();
    private final Map<String, JsonNode> started = new HashMap<>();
    private boolean running;

    /**
     * @param paths   the shared files
     * @param current the tree of a file's current content, by its path, empty when it cannot be written now
     * @param primary whether this server is the primary
     */
    public ChangeTracker(List<String> paths, Function<String, Optional<JsonNode>> current, BooleanSupplier primary) {
        this.paths = List.copyOf(paths);
        this.current = current;
        this.primary = primary;
    }

    /**
     * Takes the files as they are now as both started and applied.
     */
    public synchronized void start() {
        running = true;
        for (var path : paths) {
            current.apply(path).ifPresent(tree -> {
                started.put(path, tree);
                applied.put(path, tree);
            });
        }
    }

    /**
     * Takes the files as they are now as applied. A file that cannot be written now is left for the next time; one
     * never taken before is taken without naming changes.
     *
     * @return the settings that changed since they were last applied, as dotted keys per file
     */
    public synchronized Set<Setting> changes() {
        if (!running) return Set.of();
        var found = new LinkedHashSet<Setting>();
        for (var path : paths) {
            var tree = current.apply(path);
            if (tree.isEmpty()) continue;
            var before = applied.put(path, tree.get());
            started.putIfAbsent(path, tree.get());
            if (before == null) continue;
            Configuration.changes(before, tree.get()).forEach(key -> found.add(new Setting(path, key)));
        }
        return found;
    }

    /**
     * @return the settings read only at start that differ from what the server started with, without those only the
     *     primary uses when this server is a secondary
     */
    public synchronized List<Setting> restartPending() {
        if (!running) return List.of();
        var relevant = RESTART.stream()
                .filter(setting -> primary.getAsBoolean() || !PRIMARY_ONLY.contains(setting))
                .toList();
        var found = new LinkedHashSet<Setting>();
        for (var path : paths) {
            var start = started.get(path);
            var tree = current.apply(path);
            if (start == null || tree.isEmpty()) continue;
            for (var key : Configuration.changes(start, tree.get())) {
                relevant.stream().filter(setting -> setting.covers(path, key)).forEach(found::add);
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
