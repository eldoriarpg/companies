/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.gateway;

import de.eldoria.companies.configuration.elements.dashboard.BrandingSettings;
import de.eldoria.companies.web.api.handshake.Branding;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Reads the name and icon the dashboard shows for the server.
 * <p>
 * The name is {@code dashboard.branding.name}, or the server's MOTD without formatting when it is empty, cut to 64
 * characters. The icon is the file {@code dashboard.branding.icon} in the plugin folder, or else in the server folder,
 * where the default {@code server-icon.png} lies, so by default each server shows its own server icon. An icon named
 * {@code shared:<name>} was uploaded on the dashboard and is read from the database, so every server shows the same.
 * It must be a PNG of at most 256×256 pixels and at most 64 KB once base64 encoded; any other file is reported once and
 * left out.
 */
public final class BrandingSource {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    private static final int IHDR_END = 24;
    private static final int WIDTH_OFFSET = 16;
    private static final int HEIGHT_OFFSET = 20;

    private final Supplier<BrandingSettings> settings;
    private final Supplier<String> motd;
    private final List<Path> folders;
    private final Function<String, Optional<String>> shared;
    private final Logger logger;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    /**
     * The prefix of an icon uploaded on the dashboard and kept in the database.
     */
    public static final String SHARED = "shared:";

    /**
     * @param settings the current branding settings
     * @param motd     the server's MOTD as plain text
     * @param folders  the folders the icon is looked up in, in order: the plugin folder, then the server folder
     * @param shared   an uploaded icon as base64 by its name
     * @param logger   logger of the plugin
     */
    public BrandingSource(
            Supplier<BrandingSettings> settings,
            Supplier<String> motd,
            List<Path> folders,
            Function<String, Optional<String>> shared,
            Logger logger) {
        this.settings = settings;
        this.motd = motd;
        this.folders = List.copyOf(folders);
        this.shared = shared;
        this.logger = logger;
    }

    /**
     * Reads the branding.
     *
     * @return the branding with the icon, empty when there is neither a name nor an icon
     */
    public Optional<Branding> read() {
        var current = settings.get();
        var name = current.name().isEmpty() ? motd.get().strip() : current.name();
        name = name.replaceAll("\\s+", " ");
        if (name.length() > Branding.MAX_NAME_LENGTH) name = name.substring(0, Branding.MAX_NAME_LENGTH);
        var icon = icon(current.icon());
        if (name.isEmpty() && icon.isEmpty()) return Optional.empty();
        return Optional.of(new Branding(
                name, icon.map(Icon::base64).orElse(null), icon.map(Icon::hash).orElse(null)));
    }

    private Optional<Icon> icon(String file) {
        if (file.isEmpty()) return Optional.empty();
        if (file.startsWith(SHARED)) {
            byte[] bytes;
            try {
                bytes = shared.apply(file.substring(SHARED.length()))
                        .map(Base64.getDecoder()::decode)
                        .orElse(null);
            } catch (RuntimeException e) {
                report(file, "could not be read");
                return Optional.empty();
            }
            if (bytes == null) {
                report(file, "was not found");
                return Optional.empty();
            }
            return check(file, bytes);
        }
        for (var folder : folders) {
            var path = folder.resolve(file);
            if (!Files.isRegularFile(path)) continue;
            try {
                return check(file, Files.readAllBytes(path));
            } catch (IOException e) {
                report(file, "could not be read");
                return Optional.empty();
            }
        }
        if (!BrandingSettings.DEFAULT_ICON.equals(file)) report(file, "was not found");
        return Optional.empty();
    }

    /**
     * Checks an icon and encodes it.
     *
     * @param file  the configured name, for the report
     * @param bytes the file's content
     * @return the icon, empty when it is no PNG of the allowed size
     */
    Optional<Icon> check(String file, byte[] bytes) {
        var problem = problem(bytes);
        if (problem.isPresent()) {
            report(file, problem.get());
            return Optional.empty();
        }
        return Optional.of(new Icon(Base64.getEncoder().encodeToString(bytes), sha256(bytes)));
    }

    /**
     * @param bytes a file's content
     * @return why it cannot be the dashboard icon, empty when it is a PNG of the allowed size
     */
    public static Optional<String> problem(byte[] bytes) {
        if (bytes.length < IHDR_END || !Arrays.equals(Arrays.copyOf(bytes, PNG_SIGNATURE.length), PNG_SIGNATURE)) {
            return Optional.of("is no PNG");
        }
        var header = ByteBuffer.wrap(bytes);
        var width = header.getInt(WIDTH_OFFSET);
        var height = header.getInt(HEIGHT_OFFSET);
        if (width > Branding.MAX_ICON_SIZE || height > Branding.MAX_ICON_SIZE || width < 1 || height < 1) {
            return Optional.of("is " + width + "×" + height + " pixels, at most 256×256 are allowed");
        }
        var base64 = Base64.getEncoder().encodeToString(bytes);
        if (base64.getBytes(StandardCharsets.US_ASCII).length > Branding.MAX_ICON_ENCODED_BYTES) {
            return Optional.of("is larger than 48 KB, which is 64 KB once encoded");
        }
        return Optional.empty();
    }

    private void report(String file, String problem) {
        if (reported.add(file + problem)) {
            logger.warning("The dashboard icon " + file + " " + problem + ". The dashboard shows no icon.");
        }
    }

    /**
     * @param bytes a file's content
     * @return its SHA-256 as lower-case hex
     */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    /**
     * An icon ready to send.
     *
     * @param base64 the PNG as base64
     * @param hash   the SHA-256 of the PNG as lower-case hex
     */
    record Icon(String base64, String hash) {}
}
