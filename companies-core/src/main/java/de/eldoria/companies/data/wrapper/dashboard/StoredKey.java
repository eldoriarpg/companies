/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.dashboard;

import de.eldoria.companies.web.api.handshake.KeyKind;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The key the network authenticates with at the dashboard gateway. There is one per network. Never log {@link
 * #secret()}.
 *
 * @param secret    the Lyna dashboard key or the self-hosted gateway's server token
 * @param kind      which of the two it is
 * @param refreshAt when Lyna hands out its successor, null when unknown or for a server token
 * @param checkedAt when the primary last asked Lyna for a refresh, null when never
 * @param seedHash  the SHA-256 of the key configured in {@code node.yml} that was stored, null when it was entered
 *                  with the command
 */
public record StoredKey(
        String secret,
        KeyKind kind,
        @Nullable LocalDateTime refreshAt,
        @Nullable LocalDateTime checkedAt,
        @Nullable String seedHash) {
    /**
     * The prefix of every Lyna dashboard key.
     */
    public static final String LYNA_PREFIX = "lyd_";

    /**
     * A key as entered: a Lyna dashboard key when it starts with {@value #LYNA_PREFIX}, otherwise a server token, which
     * a self-hosted gateway prints with the prefix {@code cst_}.
     *
     * @param secret   the key
     * @param seedHash the hash of the configured key it came from, null when entered with the command
     * @return the key, never refreshed yet
     */
    public static StoredKey entered(String secret, @Nullable String seedHash) {
        var kind = secret.startsWith(LYNA_PREFIX) ? KeyKind.DASHBOARD_KEY : KeyKind.SERVER_TOKEN;
        return new StoredKey(secret, kind, null, null, seedHash);
    }

    /**
     * @return when the primary last asked Lyna, empty when never
     */
    public Optional<LocalDateTime> checked() {
        return Optional.ofNullable(checkedAt);
    }

    /**
     * @return when Lyna hands out the successor, empty when unknown
     */
    public Optional<LocalDateTime> refresh() {
        return Optional.ofNullable(refreshAt);
    }

    @Override
    public String toString() {
        return "StoredKey[kind=" + kind + ", refreshAt=" + refreshAt + ", checkedAt=" + checkedAt + "]";
    }
}
