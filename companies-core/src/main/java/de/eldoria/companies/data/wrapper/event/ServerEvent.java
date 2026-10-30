/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.event;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * A time-limited event the server runs to steer the market, stored in {@code server_events}.
 * <p>
 * While it is {@link EventState#ACTIVE active} and the time is between its start, included, and its end, excluded, its
 * effect applies to the orders in its scope: every order when both scopes are empty, otherwise only orders with a line
 * of one of its materials and only orders of companies of one of its levels. Orders worked on alone have no level, so
 * they only match events without a level scope.
 * <p>
 * Only a {@link EventKind#REWARD_MULTIPLIER} pays money and has a funding: from an economy account, within its budget
 * when one is set, or minted, always within its budget. {@code spent} is what it paid so far.
 *
 * @param id        id of the event, 0 before it is stored
 * @param name      name shown to players
 * @param kind      what it changes
 * @param value     how much it changes, see {@link EventKind}
 * @param materials the materials it is limited to, empty for every material
 * @param levels    the company levels it is limited to, empty for every company and players working alone
 * @param starts    when it starts
 * @param ends      when it ends
 * @param funding   where the money of a multiplier comes from, null for the other kinds
 * @param account   the economy account of {@link MoneySource.SourceType#ACCOUNT}, null otherwise
 * @param budget    what a multiplier may pay in all, null for no cap besides the account's balance
 * @param spent     what it paid so far
 * @param state     where it is in its life
 * @param template  the template of {@code events.yml} that created it, null for one created by hand
 * @param createdBy who created it
 * @param created   when it was created
 * @param closed    when it reached a final state, null while it runs
 * @param closedBy  who ended or cancelled it, null while it runs and when its time or its money ran out;
 *                  {@link #MISSED_BY} for one that ended without starting, {@link #TEMPLATE_BY} for one withdrawn
 *                  since its template was disabled or removed
 */
public record ServerEvent(
        int id,
        String name,
        EventKind kind,
        double value,
        List<String> materials,
        List<Integer> levels,
        LocalDateTime starts,
        LocalDateTime ends,
        MoneySource.@Nullable SourceType funding,
        @Nullable String account,
        @Nullable Double budget,
        double spent,
        EventState state,
        @Nullable String template,
        String createdBy,
        LocalDateTime created,
        @Nullable LocalDateTime closed,
        @Nullable String closedBy) {
    /**
     * The longest name of an event.
     */
    public static final int MAX_NAME_LENGTH = 64;

    /**
     * The highest reduction of {@link EventKind#TAX_REDUCTION} and {@link EventKind#FEE_REDUCTION}.
     */
    public static final double MAX_REDUCTION = 100.0;

    /**
     * What a scheduled event that ended without starting, since its whole time passed while the primary was down,
     * notes as who closed it. No player name and no dashboard admin ({@code <name>@dashboard}) can contain a
     * {@code #}, so it never names a person.
     */
    public static final String MISSED_BY = "#missed";

    /**
     * What a scheduled event notes as who closed it when the primary withdrew it, since the template of
     * {@code events.yml} that created it was disabled or removed before its start. Like {@link #MISSED_BY}, it never
     * names a person.
     */
    public static final String TEMPLATE_BY = "#template";

    /**
     * The character the scopes are stored apart with, which no material may contain.
     */
    public static final String SEPARATOR = ",";

    /**
     * @param materials the materials it is limited to
     * @param levels    the company levels it is limited to
     */
    public ServerEvent {
        materials = List.copyOf(materials);
        levels = List.copyOf(levels);
    }

    /**
     * A new event waiting for its start, not stored yet.
     *
     * @param name      name shown to players
     * @param kind      what it changes
     * @param value     how much it changes
     * @param materials the materials it is limited to, empty for every material
     * @param levels    the company levels it is limited to, empty for every company
     * @param starts    when it starts
     * @param ends      when it ends
     * @param funding   where the money of a multiplier comes from, null for the other kinds
     * @param account   the economy account of {@link MoneySource.SourceType#ACCOUNT}, null otherwise
     * @param budget    what a multiplier may pay in all, null for no cap
     * @param template  the template that created it, null for one created by hand
     * @param createdBy who created it
     * @param created   when it was created
     * @return the event
     */
    public static ServerEvent draft(
            String name,
            EventKind kind,
            double value,
            List<String> materials,
            List<Integer> levels,
            LocalDateTime starts,
            LocalDateTime ends,
            MoneySource.@Nullable SourceType funding,
            @Nullable String account,
            @Nullable Double budget,
            @Nullable String template,
            String createdBy,
            LocalDateTime created) {
        return new ServerEvent(
                0,
                name,
                kind,
                value,
                materials,
                levels,
                starts,
                ends,
                funding,
                account,
                budget,
                0,
                EventState.SCHEDULED,
                template,
                createdBy,
                created,
                null,
                null);
    }

    /**
     * Whether its effect applies at a time: it is active and the time lies between its start, included, and its end,
     * excluded. An event the primary did not start yet does not apply; one whose end passed does not apply even before
     * the primary ended it.
     *
     * @param at the time, in UTC
     * @return true when it applies
     */
    public boolean activeAt(LocalDateTime at) {
        return state == EventState.ACTIVE && !at.isBefore(starts) && at.isBefore(ends);
    }

    /**
     * @param material a material name
     * @return whether its material scope covers the material
     */
    public boolean covers(String material) {
        return materials.isEmpty() || materials.contains(material);
    }

    /**
     * Whether its scope matches an order: one of the order's materials is in its material scope, and the company's
     * level is in its level scope. An order without level, worked on alone, matches only an event without level scope.
     *
     * @param materials the materials of the order's lines
     * @param level     the level of the company, empty for an order worked on alone
     * @return true when its scope matches
     */
    public boolean matches(Collection<String> materials, OptionalInt level) {
        if (!levels.isEmpty() && (level.isEmpty() || !levels.contains(level.getAsInt()))) return false;
        return this.materials.isEmpty() || materials.stream().anyMatch(this.materials::contains);
    }

    /**
     * @return what is left of its budget, never below 0; empty when it has no budget
     */
    public OptionalDouble remaining() {
        return budget == null ? OptionalDouble.empty() : OptionalDouble.of(Math.max(0, budget - spent));
    }

    /**
     * Checks what an event needs to be stored and run.
     *
     * @return the first problem, empty when there is none
     */
    public Optional<Problem> problem() {
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH) return Optional.of(Problem.NAME);
        if (!validValue()) return Optional.of(Problem.VALUE);
        if (!ends.isAfter(starts)) return Optional.of(Problem.TIMES);
        if (!levels.isEmpty() && !kind.levelScoped()) return Optional.of(Problem.LEVEL_SCOPE);
        if (levels.stream().anyMatch(level -> level < 1)) return Optional.of(Problem.LEVEL_SCOPE);
        if (materials.stream().anyMatch(material -> material.isBlank() || material.contains(SEPARATOR))) {
            return Optional.of(Problem.MATERIALS);
        }
        if (!kind.funded()) {
            return funding == null && account == null && budget == null
                    ? Optional.empty()
                    : Optional.of(Problem.FUNDING);
        }
        if (funding == null) return Optional.of(Problem.FUNDING);
        if (funding == MoneySource.SourceType.ACCOUNT && (account == null || account.isBlank())) {
            return Optional.of(Problem.ACCOUNT);
        }
        if (funding == MoneySource.SourceType.MINTED && account != null) return Optional.of(Problem.FUNDING);
        if (funding == MoneySource.SourceType.MINTED && budget == null) return Optional.of(Problem.BUDGET);
        if (budget != null && !(budget > 0)) return Optional.of(Problem.BUDGET);
        return Optional.empty();
    }

    private boolean validValue() {
        if (!Double.isFinite(value)) return false;
        return switch (kind) {
            case TAX_REDUCTION, FEE_REDUCTION -> value > 0 && value <= MAX_REDUCTION;
            case REWARD_MULTIPLIER -> value > 1;
        };
    }

    /**
     * Why an event cannot be stored.
     */
    public enum Problem {
        /**
         * The name is blank or longer than {@link #MAX_NAME_LENGTH}.
         */
        NAME,
        /**
         * The value does not fit the kind: a reduction above 0 and at most {@link #MAX_REDUCTION}, a multiplier above
         * 1.
         */
        VALUE,
        /**
         * The end is not after the start, or when it is created already passed or lies more than a year ahead.
         */
        TIMES,
        /**
         * A fee reduction has a level scope, or a level is below 1.
         */
        LEVEL_SCOPE,
        /**
         * A material of the scope is blank, contains a comma, or names no item.
         */
        MATERIALS,
        /**
         * A multiplier has no funding, a minted one names an account, or another kind has a funding, account or
         * budget.
         */
        FUNDING,
        /**
         * A multiplier funded from an account names none.
         */
        ACCOUNT,
        /**
         * A minted multiplier has no budget, or a budget is not above 0.
         */
        BUDGET,
        /**
         * The database did not store it.
         */
        STORAGE
    }
}
