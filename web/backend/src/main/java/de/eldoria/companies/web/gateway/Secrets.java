/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Random ids, tokens, short ids and login codes. Secrets are stored only as their SHA-256 hash and compared in
 * constant time.
 */
public final class Secrets {
    /**
     * The characters of a short id: digits and capital letters without the look-alikes {@code 0}, {@code 1},
     * {@code I}, {@code L} and {@code O}.
     */
    public static final String SHORT_ID_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    /**
     * The letters of a login code: capital letters without {@code I}, {@code L} and {@code O}.
     */
    public static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ";

    /**
     * The length of short ids and login codes.
     */
    public static final int LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Secrets() {}

    /**
     * @return a session id, 128 random bits
     */
    public static String sessionId() {
        return random(16);
    }

    /**
     * @return a cookie or token value, 256 random bits
     */
    public static String token() {
        return random(32);
    }

    /**
     * @return a short id of {@value #LENGTH} characters of {@link #SHORT_ID_ALPHABET}
     */
    public static String shortId() {
        return pick(SHORT_ID_ALPHABET);
    }

    /**
     * @return a login code of {@value #LENGTH} letters of {@link #CODE_ALPHABET}
     */
    public static String code() {
        return pick(CODE_ALPHABET);
    }

    /**
     * Reads what a player typed as a short id or code: spaces and dashes are dropped, letters made capital.
     *
     * @param typed the input
     * @return the normalised input
     */
    public static String normalise(String typed) {
        return typed.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    /**
     * @param secret a token or code
     * @return its hash as stored
     */
    public static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @param secret a token or code as sent
     * @param hash the stored hash
     * @return whether the secret is the one the hash was made of, compared in constant time
     */
    public static boolean matches(String secret, String hash) {
        return MessageDigest.isEqual(
                hash(secret).getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8));
    }

    private static String pick(String alphabet) {
        StringBuilder value = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) value.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return value.toString();
    }

    private static String random(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
