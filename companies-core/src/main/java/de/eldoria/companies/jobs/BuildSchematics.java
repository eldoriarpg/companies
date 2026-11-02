/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.jobs.hooks.JobHooks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The schematics build jobs may use: the server's, in the plugin's {@code schematics} folder of this node, and the ones
 * a player uploaded, which come first when both share a name. The methods read files and the database; call them off
 * the main thread.
 */
public final class BuildSchematics {
    private final Path folder;
    private final SchematicUploads uploads;
    private final JobHooks hooks;
    private final Logger logger;

    /**
     * @param folder  the server's schematics folder
     * @param uploads the schematics players uploaded
     * @param hooks   reads schematic files
     * @param logger  reports a folder that can not be read
     */
    public BuildSchematics(Path folder, SchematicUploads uploads, JobHooks hooks, Logger logger) {
        this.folder = folder;
        this.uploads = uploads;
        this.hooks = hooks;
        this.logger = logger;
    }

    /**
     * The schematics a player may build.
     *
     * @param server the server's, sorted
     * @param own    the player's uploads, sorted
     */
    public record Names(List<String> server, List<String> own) {}

    /**
     * @param owner a player
     * @return the schematics the player may build
     */
    public Names names(UUID owner) {
        return new Names(
                files().stream()
                        .map(file -> stripEnding(String.valueOf(file.getFileName())))
                        .sorted()
                        .toList(),
                uploads.names(owner));
    }

    /**
     * @param owner     the player building it
     * @param name      the name of a schematic, compared ignoring case
     * @param rotation  degrees to turn it clockwise seen from above
     * @param maxVolume the most blocks its box may hold
     * @return its blueprint, the player's upload before the server's; empty when there is none of that name or it can
     *     not be read
     */
    public Optional<Blueprint> blueprint(UUID owner, String name, int rotation, int maxVolume) {
        return uploads.blueprint(owner, name, rotation, maxVolume)
                .or(() -> file(name).flatMap(file -> hooks.schematic(file, rotation, maxVolume)));
    }

    /**
     * @param name a schematic name as the player typed it
     * @return the file of the schematic with that name in the folder, ignoring case; only files listed in the folder
     *     are found, so no name reaches outside it
     */
    private Optional<Path> file(String name) {
        return files().stream()
                .filter(file -> stripEnding(String.valueOf(file.getFileName())).equalsIgnoreCase(name))
                .findFirst();
    }

    private List<Path> files() {
        try {
            Files.createDirectories(folder);
            try (var files = Files.list(folder)) {
                return files.filter(Files::isRegularFile).toList();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read the schematics folder " + folder, e);
            return List.of();
        }
    }

    private static String stripEnding(String fileName) {
        var dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
