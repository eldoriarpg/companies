/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The players who took the admin sandbox on this server and whose dashboard sessions were not ended since, with their
 * names, kept in a file so a restart does not forget them; an entry without a name is skipped. Only the main thread uses it; every change is written at
 * once, and a file that can not be written is logged and kept in memory.
 */
final class SandboxLedger {
    private final Path file;
    private final Logger logger;
    private final Map<UUID, String> players = new LinkedHashMap<>();

    /**
     * Reads the file; a missing or unreadable one starts empty.
     *
     * @param file   where the players are kept
     * @param logger told about files that can not be read or written
     */
    SandboxLedger(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
        if (!Files.exists(file)) return;
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        for (var key : yaml.getKeys(false)) {
            try {
                var name = yaml.getString(key);
                if (name != null && !name.isBlank()) players.put(UUID.fromString(key), name);
            } catch (IllegalArgumentException e) {
                logger.warning("Skipped " + key + " in " + file.getFileName() + "; it is no player id");
            }
        }
    }

    /**
     * @param player id of the player
     * @param name   their name
     */
    void add(UUID player, String name) {
        if (name.equals(players.put(player, name))) return;
        save();
    }

    /**
     * @param player id of the player
     */
    void remove(UUID player) {
        if (players.remove(player) != null) save();
    }

    /**
     * @param player id of the player
     * @return whether the player is kept
     */
    boolean contains(UUID player) {
        return players.containsKey(player);
    }

    /**
     * @return the players kept, with their names
     */
    Map<UUID, String> all() {
        return Map.copyOf(players);
    }

    private void save() {
        var yaml = new YamlConfiguration();
        players.forEach((player, name) -> yaml.set(player.toString(), name));
        try {
            var folder = file.getParent();
            if (folder != null) Files.createDirectories(folder);
            yaml.save(file.toFile());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not write " + file.getFileName(), e);
        }
    }
}
