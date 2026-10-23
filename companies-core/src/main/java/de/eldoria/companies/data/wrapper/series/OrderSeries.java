/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.series;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * A repeating order: the template from which a normal order is posted every interval. Only its owner sees it;
 * companies see the copies as ordinary orders.
 *
 * @param id           id of the series, -1 before it is saved
 * @param owner        player who pays and receives every copy
 * @param name         name of every copy
 * @param lines        lines of every copy
 * @param interval     how often a copy is posted
 * @param maxCopies    how many copies are posted at most, or empty without count limit
 * @param ends         after which time no copy is posted, or empty without end date
 * @param posted       copies posted so far
 * @param nextDue      when the next copy is due
 * @param currentOrder the last posted copy, or empty before the first one
 * @param state        whether copies are still posted
 * @param stopReason   why the series was stopped, empty unless it is stopped
 * @param waiting      why a due copy is held back, or empty
 * @param orderLimit   the owner's order limit, refreshed while the owner is online
 * @param created      when the series was created
 */
public record OrderSeries(
        int id,
        UUID owner,
        String name,
        List<SeriesLine> lines,
        RepeatInterval interval,
        OptionalInt maxCopies,
        Optional<LocalDateTime> ends,
        int posted,
        LocalDateTime nextDue,
        OptionalInt currentOrder,
        SeriesState state,
        Optional<SeriesStopReason> stopReason,
        Optional<SeriesWait> waiting,
        int orderLimit,
        LocalDateTime created) {
    /**
     * Creates a series. The lines are copied.
     */
    public OrderSeries {
        lines = List.copyOf(lines);
    }

    /**
     * A new, active series whose first copy is due at once.
     *
     * @param owner      player who pays and receives every copy
     * @param name       name of every copy
     * @param lines      lines of every copy
     * @param interval   how often a copy is posted
     * @param maxCopies  how many copies are posted at most, or empty
     * @param ends       after which time no copy is posted, or empty
     * @param orderLimit the owner's current order limit
     * @param now        current time
     * @return unsaved series
     */
    public static OrderSeries draft(
            UUID owner,
            String name,
            List<SeriesLine> lines,
            RepeatInterval interval,
            OptionalInt maxCopies,
            Optional<LocalDateTime> ends,
            int orderLimit,
            LocalDateTime now) {
        return new OrderSeries(
                -1,
                owner,
                name,
                lines,
                interval,
                maxCopies,
                ends,
                0,
                now,
                OptionalInt.empty(),
                SeriesState.ACTIVE,
                Optional.empty(),
                Optional.empty(),
                orderLimit,
                now);
    }

    /**
     * Whether the series may post no further copy: its count limit is reached or its end date has passed.
     *
     * @param now current time
     * @return true when no further copy may be posted
     */
    public boolean endReached(LocalDateTime now) {
        if (maxCopies.isPresent() && posted >= maxCopies.getAsInt()) return true;
        return ends.map(now::isAfter).orElse(false);
    }

    /**
     * The same series with an id, as returned after saving.
     *
     * @param id id of the saved series
     * @return saved series
     */
    public OrderSeries withId(int id) {
        return new OrderSeries(
                id,
                owner,
                name,
                lines,
                interval,
                maxCopies,
                ends,
                posted,
                nextDue,
                currentOrder,
                state,
                stopReason,
                waiting,
                orderLimit,
                created);
    }
}
