/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import de.eldoria.companies.components.event.EventKind;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * A server event to create with {@link de.eldoria.companies.api.ServerEventApi#create}.
 * <p>
 * The same rules apply as for {@code /companyadmin event create}: a name of at most 64 characters, a value that fits
 * the kind, an end after the start and at most a year ahead, materials that name items, and no level scope on a fee
 * reduction. A {@link EventKind#REWARD_MULTIPLIER} needs money, set with {@link #mintedWithin(double)} or
 * {@link #paidFrom(String, Double)}; without either it is rejected as {@link EventProblem#ACCOUNT}. The other kinds pay
 * nothing and ignore the money fields. Materials are stored by their canonical names.
 *
 * @param name      name shown to players
 * @param kind      what it changes
 * @param value     how much it changes, see {@link EventKind}
 * @param materials the materials it is limited to, empty for every material
 * @param levels    the company levels it is limited to, empty for every company
 * @param starts    when it starts, in UTC
 * @param ends      when it ends, in UTC
 * @param minted    whether a multiplier's money is created by the server instead of taken from {@code account}
 * @param account   the economy account a multiplier is paid from, null for minted money
 * @param budget    what a multiplier may pay in all, null for no cap besides the account's balance
 */
public record EventRequest(
        String name,
        EventKind kind,
        double value,
        List<String> materials,
        List<Integer> levels,
        LocalDateTime starts,
        LocalDateTime ends,
        boolean minted,
        @Nullable String account,
        @Nullable Double budget) {
    /**
     * Copies the scopes.
     */
    public EventRequest {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(starts, "starts");
        Objects.requireNonNull(ends, "ends");
        materials = List.copyOf(materials);
        levels = List.copyOf(levels);
    }

    /**
     * An event that pays nothing, such as a tax or fee reduction, for every order.
     *
     * @param name   name shown to players
     * @param kind   what it changes
     * @param value  how much it changes
     * @param starts when it starts, in UTC
     * @param ends   when it ends, in UTC
     * @return the request
     */
    public static EventRequest of(String name, EventKind kind, double value, LocalDateTime starts, LocalDateTime ends) {
        return new EventRequest(name, kind, value, List.of(), List.of(), starts, ends, false, null, null);
    }

    /**
     * A copy limited to materials.
     *
     * @param materials the materials it is limited to, empty for every material
     * @return a copy limited to the materials
     */
    public EventRequest withMaterials(List<String> materials) {
        return new EventRequest(name, kind, value, materials, levels, starts, ends, minted, account, budget);
    }

    /**
     * A copy limited to company levels.
     *
     * @param levels the company levels it is limited to, empty for every company
     * @return a copy limited to the levels
     */
    public EventRequest withLevels(List<Integer> levels) {
        return new EventRequest(name, kind, value, materials, levels, starts, ends, minted, account, budget);
    }

    /**
     * A copy paid with minted money.
     *
     * @param budget what the multiplier may pay in all
     * @return a copy paid with minted money within the budget
     */
    public EventRequest mintedWithin(double budget) {
        return new EventRequest(name, kind, value, materials, levels, starts, ends, true, null, budget);
    }

    /**
     * A copy paid from an economy account.
     *
     * @param account the economy account paying the multiplier
     * @param budget  what it may pay in all, null for no cap besides the balance
     * @return a copy paid from the account
     */
    public EventRequest paidFrom(String account, @Nullable Double budget) {
        return new EventRequest(name, kind, value, materials, levels, starts, ends, false, account, budget);
    }
}
