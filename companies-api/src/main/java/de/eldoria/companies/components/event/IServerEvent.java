/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A time-limited event the server runs to steer the market: a tax reduction, a reward multiplier or a fee reduction.
 * <p>
 * While it is {@link EventState#ACTIVE} and the time lies between its start, included, and its end, excluded, its
 * effect applies to the orders in its scope: every order when both scopes are empty, otherwise only orders with a line
 * of one of its materials and only orders of companies of one of its levels. Orders worked on alone only match events
 * without a level scope. All times are UTC.
 */
public interface IServerEvent {
    /**
     * The id of the event.
     *
     * @return id
     */
    int id();

    /**
     * The name shown to players.
     *
     * @return name
     */
    String name();

    /**
     * What the event changes.
     *
     * @return kind
     */
    EventKind kind();

    /**
     * How much it changes: percentage points off the tax, the factor on the gross payout, or percent off the fee.
     *
     * @return value, see {@link EventKind}
     */
    double value();

    /**
     * The materials the event is limited to.
     *
     * @return material names, empty for every material
     */
    List<String> materials();

    /**
     * The company levels the event is limited to.
     *
     * @return levels, empty for every company and for players working alone
     */
    List<Integer> levels();

    /**
     * When the event starts.
     *
     * @return start in UTC
     */
    LocalDateTime starts();

    /**
     * When the event ends.
     *
     * @return end in UTC
     */
    LocalDateTime ends();

    /**
     * Whether the money of a multiplier is created by the server instead of taken from an account.
     *
     * @return true for minted money, false for an account and for the kinds that pay nothing
     */
    boolean minted();

    /**
     * The economy account a multiplier is paid from.
     *
     * @return the account, empty for minted money and for the kinds that pay nothing
     */
    Optional<String> fundingAccount();

    /**
     * What a multiplier may pay in all.
     *
     * @return the budget, empty when only the account's balance limits it and for the kinds that pay nothing
     */
    OptionalDouble budgetCap();

    /**
     * What a multiplier paid so far.
     *
     * @return money paid
     */
    double spent();

    /**
     * Where the event is in its life.
     *
     * @return state
     */
    EventState state();

    /**
     * The template of {@code events.yml} that created the event.
     *
     * @return template id, empty for an event created by hand, by a plugin or as a goal reward
     */
    Optional<String> templateId();

    /**
     * Who created the event: a player's name, {@code <name>@dashboard}, {@code <plugin>@api}, or {@code goal #<id>}
     * for the reward of an event offer goal.
     *
     * @return creator
     */
    String createdBy();

    /**
     * When the event was created.
     *
     * @return creation time in UTC
     */
    LocalDateTime created();

    /**
     * When the event reached a final state.
     *
     * @return time in UTC, empty while it runs
     */
    Optional<LocalDateTime> closedAt();

    /**
     * Who ended or cancelled the event.
     *
     * @return the admin or plugin, {@code #missed} or {@code #template}; empty while it runs and when its time or its
     *     money ran out
     */
    Optional<String> closer();
}
