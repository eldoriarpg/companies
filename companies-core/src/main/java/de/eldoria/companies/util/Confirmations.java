/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the actions players were asked to confirm.
 * <p>
 * A confirmation belongs to one player, is valid for {@link #VALIDITY} and can be used once. Requesting a new one
 * replaces the pending one of that player.
 *
 * @param <T> the value the confirmation carries until it is confirmed
 */
public final class Confirmations<T> {
    /**
     * How long a confirmation stays valid.
     */
    public static final Duration VALIDITY = Duration.ofSeconds(30);
    private final Map<UUID, Pending<T>> pending = new ConcurrentHashMap<>();
    private final InstantSource clock;

    /**
     * Creates confirmations that expire by the system clock.
     */
    public Confirmations() {
        this(InstantSource.system());
    }

    /**
     * Creates confirmations that expire by the given clock.
     *
     * @param clock source of the current time
     */
    public Confirmations(InstantSource clock) {
        this.clock = clock;
    }

    /**
     * Asks a player to confirm a value. Expired confirmations of all players are dropped on the way.
     *
     * @param player player who has to confirm
     * @param value  value returned once the player confirms
     */
    public void request(UUID player, T value) {
        var now = clock.instant();
        pending.values().removeIf(entry -> !now.isBefore(entry.expiry()));
        pending.put(player, new Pending<>(value, now.plus(VALIDITY)));
    }

    /**
     * Takes the pending confirmation of a player. It is removed either way, so it can be used only once.
     *
     * @param player player who confirms
     * @return the value, or empty when nothing was pending or the confirmation expired
     */
    public Optional<T> confirm(UUID player) {
        var entry = pending.remove(player);
        if (entry == null || !clock.instant().isBefore(entry.expiry())) return Optional.empty();
        return Optional.of(entry.value());
    }

    /**
     * Checks whether a player has a confirmation that is still valid.
     *
     * @param player player to check
     * @return true when a confirmation is pending and not expired
     */
    public boolean isPending(UUID player) {
        var entry = pending.get(player);
        return entry != null && clock.instant().isBefore(entry.expiry());
    }

    private record Pending<T>(T value, Instant expiry) {
    }
}
