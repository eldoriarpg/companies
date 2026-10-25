/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Tells which settings of the shared files changed since they were last applied, so only the work that depends on them
 * runs, and which settings read only at start this server now uses with another value than it started with.
 * <p>
 * Each file is kept as the tree its content serializes to; the start values are kept as this server uses them, its
 * own overrides included. Until {@link #start()} ran, nothing counts as changed. When the start values cannot be read
 * at start, the first ones read later count as the start values.
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

    /** Whether chat is held back while a chat menu is open. */
    public static final Setting MESSAGE_BLOCKING = Setting.of(CONFIG, "generalSettings.messageBlocking");
    /** The switches of the plugin hooks. */
    public static final Setting HOOKS = Setting.of(CONFIG, "generalSettings.hooks");
    /** Whether order boards are on. */
    public static final Setting BOARDS_ENABLED = Setting.of(CONFIG, "boards.enabled");
    /** How many offline players the placeholders keep. */
    public static final Setting OFFLINE_CACHE = Setting.of(CONFIG, "placeholders.offlineCacheSize");
    /** How often the primary looks for expired orders. */
    public static final Setting ORDER_CHECK = Setting.of(CONFIG, "generalSettings.orderCheckInterval");
    /** How often the primary posts due repeating orders. */
    public static final Setting REPEATING_CHECK = Setting.of("order.yml", "repeating.checkMinutes");

    /**
     * The settings that are only read when the server starts.
     */
    public static final List<Setting> RESTART =
            List.of(ORDER_CHECK, MESSAGE_BLOCKING, HOOKS, OFFLINE_CACHE, BOARDS_ENABLED, REPEATING_CHECK);

    /**
     * The settings of {@link #RESTART} only the primary uses, so a secondary needs no restart for them.
     */
    public static final List<Setting> PRIMARY_ONLY = List.of(ORDER_CHECK, REPEATING_CHECK);

    private final List<String> paths;
    private final Function<String, Optional<JsonNode>> current;
    private final BooleanSupplier primary;
    private final Supplier<Map<Setting, Object>> restartValues;
    private @Nullable Map<Setting, Object> startValues;
    private List<Setting> lastPending = List.of();
    private final Map<String, JsonNode> applied = new HashMap<>();
    private boolean running;

    /**
     * @param paths         the shared files
     * @param current       the tree of a file's current content, by its path, empty when it cannot be written now
     * @param primary       whether this server is the primary
     * @param restartValues the value of each setting of {@link #RESTART} on this server, with its own overrides
     */
    public ChangeTracker(
            List<String> paths,
            Function<String, Optional<JsonNode>> current,
            BooleanSupplier primary,
            Supplier<Map<Setting, Object>> restartValues) {
        this.paths = List.copyOf(paths);
        this.current = current;
        this.primary = primary;
        this.restartValues = restartValues;
    }

    /**
     * Takes the files as they are now as applied and the values of the settings read only at start as the start values.
     */
    public synchronized void start() {
        running = true;
        startValues = readRestartValues().orElse(null);
        for (var path : paths) {
            current.apply(path).ifPresent(tree -> applied.put(path, tree));
        }
    }

    private Optional<Map<Setting, Object>> readRestartValues() {
        try {
            return Optional.of(Map.copyOf(restartValues.get()));
        } catch (RuntimeException e) {
            return Optional.empty();
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
            if (before == null) continue;
            Configuration.changes(before, tree.get()).forEach(key -> found.add(new Setting(path, key)));
        }
        return found;
    }

    /**
     * @return the settings read only at start whose value on this server differs from the one it started with, its
     *     own overrides included, without those only the primary uses when this server is a secondary; the last answer
     *     while the values cannot be read, for example while a section is empty
     */
    public synchronized List<Setting> restartPending() {
        if (!running) return List.of();
        var now = readRestartValues();
        if (now.isEmpty()) return lastPending;
        if (startValues == null) startValues = now.get();
        var start = startValues;
        lastPending = RESTART.stream()
                .filter(setting -> primary.getAsBoolean() || !PRIMARY_ONLY.contains(setting))
                .filter(setting -> !Objects.equals(start.get(setting), now.get().get(setting)))
                .toList();
        return lastPending;
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
