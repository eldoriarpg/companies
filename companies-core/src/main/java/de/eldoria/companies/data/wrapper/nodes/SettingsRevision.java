/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.nodes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A revision of a file the nodes of a network share, as the database holds it.
 *
 * @param path     the file, such as {@code company.yml}
 * @param revision its revision, counting from 1
 * @param content  the file's content
 * @param hash     the SHA-256 of the content
 */
public record SettingsRevision(String path, int revision, String content, String hash) {
    /**
     * @param content a file's content
     * @return its SHA-256 as hex
     */
    public static String hash(String content) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }
}
