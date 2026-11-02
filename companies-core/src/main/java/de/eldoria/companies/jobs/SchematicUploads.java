/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.data.repository.AJobData;
import de.eldoria.companies.jobs.hooks.JobHooks;
import de.eldoria.companies.jobs.hooks.SchematicCleaner;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The schematics players upload for their build jobs. Uploads are off unless the server switches them on
 * ({@link JobSettings#schematicUploads()}) and FastAsyncWorldEdit is installed, which the cleaner needs. Every upload is cleaned before it is
 * kept: the contents of containers, command blocks, creatures and other unwanted contents are removed. A schematic is
 * kept only when it can be read and fits the size of build jobs, which is checked on the size its file declares before
 * anything loads it. It is stored in the database, so every server can use it. Blocks; call it off the main thread.
 */
public final class SchematicUploads {
    /**
     * The largest schematic file taken, in bytes.
     */
    public static final int MAX_BYTES = 512 * 1024;

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private final AJobData jobs;
    private final JobHooks hooks;
    private final Supplier<JobSettings> settings;
    private final Supplier<LocalDateTime> now;
    private final Logger logger;

    /**
     * @param jobs     job repository, which keeps the schematics
     * @param hooks    reads schematics and cleans them
     * @param settings the settings of jobs
     * @param now      the current time
     * @param logger   reports schematics that could not be cleaned
     */
    public SchematicUploads(
            AJobData jobs, JobHooks hooks, Supplier<JobSettings> settings, Supplier<LocalDateTime> now, Logger logger) {
        this.jobs = jobs;
        this.hooks = hooks;
        this.settings = settings;
        this.now = now;
        this.logger = logger;
    }

    /**
     * Why an upload was not kept.
     */
    public enum Reason {
        /**
         * The server does not take uploads.
         */
        OFF,
        /**
         * The name is not 1 to 32 lower case letters, digits, dashes or underscores.
         */
        NAME,
        /**
         * The file is larger than {@link #MAX_BYTES}.
         */
        TOO_LARGE,
        /**
         * The player keeps as many schematics as allowed.
         */
        LIMIT,
        /**
         * The file is no schematic, holds nothing a build job asks for, is larger than a build job may be or could not
         * be cleaned.
         */
        UNREADABLE
    }

    /**
     * What came of an upload.
     */
    public sealed interface Uploaded {
        /**
         * The cleaned schematic was kept.
         *
         * @param name    its name
         * @param cleaned what cleaning removed
         * @param blocks  how many blocks its blueprint asks for
         */
        record Stored(String name, SchematicCleaner.Cleaned cleaned, int blocks) implements Uploaded {}

        /**
         * Nothing was kept.
         *
         * @param reason why
         */
        record Refused(Reason reason) implements Uploaded {}
    }

    /**
     * @return whether the server takes uploads now
     */
    public boolean open() {
        return settings.get().schematicUploads() && hooks.cleaner().isPresent() && hooks.selects();
    }

    /**
     * Cleans and keeps a schematic a player uploaded, replacing one of the same name.
     *
     * @param owner the player
     * @param name  the name to keep it under, compared ignoring case
     * @param file  the schematic file
     * @return what came of it
     * @throws IllegalStateException when the cleaned schematic could not be stored
     */
    public Uploaded upload(UUID owner, String name, byte[] file) {
        var cleaner = hooks.cleaner();
        if (!open() || cleaner.isEmpty()) return new Uploaded.Refused(Reason.OFF);
        var key = name.strip().toLowerCase(Locale.ROOT);
        if (!NAME.matcher(key).matches()) return new Uploaded.Refused(Reason.NAME);
        if (file.length > MAX_BYTES) return new Uploaded.Refused(Reason.TOO_LARGE);
        var kept = jobs.schematicNames(owner);
        if (!kept.contains(key) && kept.size() >= settings.get().maxSchematics()) {
            return new Uploaded.Refused(Reason.LIMIT);
        }
        var maxVolume = settings.get().buildMaxVolume();
        Path in = null;
        Path out = null;
        SchematicCleaner.Cleaned cleaned;
        Blueprint blueprint;
        byte[] stored;
        try {
            in = Files.createTempFile("companies-upload", ".gz");
            Files.write(in, file);
            var header = fitting(in, maxVolume);
            if (header.isEmpty()) return new Uploaded.Refused(Reason.UNREADABLE);
            var typed = in.resolveSibling(in.getFileName() + header.get().suffix());
            Files.move(in, typed);
            in = typed;
            out = Files.createTempFile("companies-cleaned", ".schem");
            cleaned = cleaner.get().clean(in, out);
            var read = fitting(out, maxVolume).isPresent()
                    ? hooks.schematic(out, 0, maxVolume)
                    : Optional.<Blueprint>empty();
            if (read.isEmpty()) return new Uploaded.Refused(Reason.UNREADABLE);
            blueprint = read.get();
            stored = Files.readAllBytes(out);
        } catch (IOException | RuntimeException | LinkageError e) {
            logger.log(Level.FINE, "Could not clean the schematic " + key + " of " + owner, e);
            return new Uploaded.Refused(Reason.UNREADABLE);
        } finally {
            delete(in);
            delete(out);
        }
        jobs.saveSchematic(owner, key, Base64.getEncoder().encodeToString(stored), now.get());
        return new Uploaded.Stored(key, cleaned, blueprint.blocks());
    }

    /**
     * @param owner a player
     * @return the names of the player's schematics, sorted
     */
    public List<String> names(UUID owner) {
        return jobs.schematicNames(owner);
    }

    /**
     * @param owner a player
     * @param name  the name of one of the player's schematics, compared ignoring case
     * @return true when it existed and is gone
     */
    public boolean delete(UUID owner, String name) {
        return jobs.deleteSchematic(owner, name.strip().toLowerCase(Locale.ROOT));
    }

    /**
     * @param owner     a player
     * @param name      the name of one of the player's schematics, compared ignoring case
     * @param rotation  degrees to turn it clockwise seen from above
     * @param maxVolume the most blocks its box may hold
     * @return its blueprint, empty when the player has none of that name or it can not be read
     */
    public Optional<Blueprint> blueprint(UUID owner, String name, int rotation, int maxVolume) {
        var data = jobs.schematic(owner, name.strip().toLowerCase(Locale.ROOT));
        if (data.isEmpty()) return Optional.empty();
        Path file = null;
        try {
            file = Files.createTempFile("companies-schematic", ".schem");
            Files.write(file, Base64.getDecoder().decode(data.get()));
            if (fitting(file, maxVolume).isEmpty()) return Optional.empty();
            return hooks.schematic(file, rotation, maxVolume);
        } catch (IOException | IllegalArgumentException e) {
            logger.log(Level.WARNING, "Could not read the schematic " + name + " of " + owner, e);
            return Optional.empty();
        } finally {
            delete(file);
        }
    }

    private static Optional<SchematicHeader> fitting(Path file, int maxVolume) {
        return SchematicHeader.read(file).filter(header -> header.volume() <= maxVolume);
    }

    private void delete(@Nullable Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not delete " + file, e);
        }
    }
}
