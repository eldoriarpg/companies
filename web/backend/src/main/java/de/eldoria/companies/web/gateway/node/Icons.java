/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.node;

import de.eldoria.companies.web.api.handshake.Branding;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Checks the server icons nodes send: a base64 PNG within the size limits of {@link Branding}, whose SHA-256 is the
 * hash the node named.
 */
public final class Icons {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    private static final int HEADER_LENGTH = 24;

    private Icons() {}

    /**
     * @param hash a text
     * @return whether it has the form of an icon hash: 64 lower-case hex digits
     */
    public static boolean isHash(String hash) {
        return HASH.matcher(hash).matches();
    }

    /**
     * @param icon the base64 PNG
     * @param hash the hash the node named
     * @return the PNG bytes, none when the icon breaks a limit or does not match the hash
     */
    public static Optional<byte[]> decode(String icon, String hash) {
        if (icon.length() > Branding.MAX_ICON_ENCODED_BYTES || !isHash(hash)) return Optional.empty();
        byte[] png;
        try {
            png = Base64.getDecoder().decode(icon);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (png.length < HEADER_LENGTH || !Arrays.equals(png, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, 8)) {
            return Optional.empty();
        }
        ByteBuffer header = ByteBuffer.wrap(png, 16, 8);
        int width = header.getInt();
        int height = header.getInt();
        if (width <= 0 || height <= 0 || width > Branding.MAX_ICON_SIZE || height > Branding.MAX_ICON_SIZE) {
            return Optional.empty();
        }
        return sha256(png).equals(hash) ? Optional.of(png) : Optional.empty();
    }

    /**
     * @param bytes the bytes
     * @return their SHA-256 as lower-case hex
     */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
