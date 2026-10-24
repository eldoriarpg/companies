/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.keys;

import de.eldoria.companies.data.repository.ADashboardData;
import de.eldoria.companies.data.wrapper.dashboard.StoredKey;
import de.eldoria.companies.web.api.handshake.KeyKind;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The key the network authenticates with at the gateway, kept in the database so every node sees a rotation.
 * <p>
 * A key is entered with {@code /companyadmin dashboard key} or configured in {@code node.yml} of the primary; a
 * configured key is stored only when it differs from the one stored from the configuration before, so it undoes
 * neither a rotation nor an entered key. A key
 * starting with {@code lyd_} is a Lyna dashboard key, anything else a self-hosted gateway's server token.
 * <p>
 * The primary asks Lyna for the current dashboard key at most once a day, see {@link #refreshIfDue()}, and stores the
 * answer before it is used. A server token is never refreshed. No method logs a key.
 */
public final class DashboardKeys {
    /**
     * How long the primary waits between two refreshes.
     */
    public static final Duration REFRESH_INTERVAL = Duration.ofDays(1);

    private final ADashboardData data;
    private final LynaKeys lyna;
    private final Clock clock;
    private final Logger logger;
    private volatile Optional<String> problem = Optional.empty();
    private volatile Optional<StoredKey> unsaved = Optional.empty();

    /**
     * @param data   the dashboard tables
     * @param lyna   asks Lyna for the current key
     * @param clock  the current time
     * @param logger logger of the plugin
     */
    public DashboardKeys(ADashboardData data, LynaKeys lyna, Clock clock, Logger logger) {
        this.data = data;
        this.lyna = lyna;
        this.clock = clock;
        this.logger = logger;
    }

    /**
     * The key the network uses now: a rotated key that could not be stored yet, otherwise the one in the database.
     * Storing a rotated key is tried again first.
     *
     * @return the key, empty when none was entered
     */
    public Optional<StoredKey> current() {
        saveUnsaved();
        var pending = unsaved;
        return pending.isPresent() ? pending : data.key();
    }

    /**
     * Stores a key entered by an admin. It replaces the stored one and is refreshed on the next check.
     * <p>
     * The hash of the key configured in this node's {@code node.yml} is kept with it, or the hash stored before when
     * none is configured, so a reload does not put the configured key back over the entered one. Only a changed
     * configured key replaces it again.
     *
     * @param secret     the key or token
     * @param configured the resolved key configured in this node's {@code node.yml}, empty when none is configured
     * @return the stored key
     */
    public StoredKey enter(String secret, Optional<String> configured) {
        var seedHash = configured
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(DashboardKeys::sha256)
                .orElseGet(() -> data.key().map(StoredKey::seedHash).orElse(null));
        var key = StoredKey.entered(secret.trim(), seedHash);
        data.saveKey(key, now());
        unsaved = Optional.empty();
        problem = Optional.empty();
        logger.info("A new dashboard " + describe(key.kind()) + " was stored.");
        return key;
    }

    /**
     * Stores the key configured in {@code node.yml} when it differs from the one last stored from there.
     *
     * @param configured the resolved configured key, empty when none is configured
     * @return true when it was stored
     */
    public boolean seed(Optional<String> configured) {
        var secret = configured.map(String::trim).filter(value -> !value.isEmpty());
        if (secret.isEmpty()) return false;
        var hash = sha256(secret.get());
        var stored = data.key();
        if (stored.isPresent() && hash.equals(stored.get().seedHash())) return false;
        var key = StoredKey.entered(secret.get(), hash);
        data.saveKey(key, now());
        unsaved = Optional.empty();
        problem = Optional.empty();
        logger.info("The dashboard " + describe(key.kind()) + " from node.yml was stored.");
        return true;
    }

    /**
     * Asks Lyna for the current dashboard key when the last check is a day ago or longer, and stores the answer. A
     * server token, a missing key and a failed request change nothing; a failed request is tried again on the next
     * call.
     *
     * @return the key to use now, empty when none is stored
     */
    public Optional<StoredKey> refreshIfDue() {
        var stored = current();
        if (stored.isEmpty() || stored.get().kind() != KeyKind.DASHBOARD_KEY) return stored;
        var key = stored.get();
        var now = now();
        if (key.checked()
                .filter(checked -> checked.plus(REFRESH_INTERVAL).isAfter(now))
                .isPresent()) return stored;
        try {
            return Optional.of(apply(key, lyna.refresh(key.secret()), now));
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not refresh the dashboard key: " + e.getMessage());
            return stored;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return stored;
        }
    }

    private StoredKey apply(StoredKey key, LynaKeys.Answer answer, LocalDateTime now) {
        return switch (answer) {
            case LynaKeys.Issued issued -> issued(key, issued, now);
            case LynaKeys.Refused refused -> refused(key, refused, now);
        };
    }

    private StoredKey issued(StoredKey key, LynaKeys.Issued issued, LocalDateTime now) {
        var refreshAt = LocalDateTime.ofInstant(issued.refreshAt(), ZoneOffset.UTC);
        var next = new StoredKey(issued.key(), KeyKind.DASHBOARD_KEY, refreshAt, now, key.seedHash());
        try {
            data.saveKey(next, now);
        } catch (RuntimeException e) {
            unsaved = Optional.of(next);
            logger.log(
                    Level.WARNING,
                    "Could not store the rotated dashboard key. It is used and stored again on the next check.",
                    e);
        }
        problem = Optional.empty();
        if (!issued.key().equals(key.secret())) logger.info("The dashboard key was rotated.");
        return next;
    }

    /**
     * Stores a rotated key that could not be stored before. The other nodes keep the previous key, which Lyna accepts
     * for ten more minutes, so this is tried on every check.
     */
    private void saveUnsaved() {
        var pending = unsaved;
        if (pending.isEmpty()) return;
        try {
            data.saveKey(pending.get(), now());
            unsaved = Optional.empty();
            logger.info("The rotated dashboard key was stored.");
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "The rotated dashboard key still could not be stored.", e);
        }
    }

    private StoredKey refused(StoredKey key, LynaKeys.Refused refused, LocalDateTime now) {
        data.keyChecked(now);
        if (!refused.keyLost()) return key;
        var message = "Lyna refused the dashboard key (" + refused.reason()
                + "). Create a new key in the Lyna account area and enter it with /companyadmin dashboard key.";
        if (!problem.equals(Optional.of(refused.reason()))) logger.warning(message);
        problem = Optional.of(refused.reason());
        return key;
    }

    /**
     * Why Lyna refused the key on the last check.
     *
     * @return Lyna's reason, e.g. {@code EXPIRED}, empty while the key works
     */
    public Optional<String> problem() {
        return problem;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String describe(KeyKind kind) {
        return kind == KeyKind.DASHBOARD_KEY ? "key" : "server token";
    }

    /**
     * Hashes a configured key, so the database holds which one was stored without holding it twice.
     *
     * @param secret the key
     * @return the SHA-256 as lower-case hex
     */
    static String sha256(String secret) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
