/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The server events of the network.
 * <p>
 * Every change is conditional on what the caller read: a state moves only from the state it is in, an end moves only
 * from the end it has, so the primary's job and an admin's command on any node, or two nodes, never move an event
 * twice; the loser of such a race sees {@code false} and reads again. Methods block; call them off the main thread.
 */
public abstract class AServerEventData {
    /**
     * Stores a new event.
     *
     * @param event the event; its id, spent money and closing are ignored
     * @return the id of the stored event, empty when it could not be stored
     */
    public abstract Optional<Integer> create(ServerEvent event);

    /**
     * @param id id of the event
     * @return the event, empty when there is none
     */
    public abstract Optional<ServerEvent> event(int id);

    /**
     * @param limit how many to return at most
     * @return the newest events, newest first
     */
    public abstract List<ServerEvent> events(int limit);

    /**
     * The events that wait for their start or run, which are all that may apply now or later.
     *
     * @return the events, the soonest start first
     */
    public abstract List<ServerEvent> live();

    /**
     * Moves an event from one state to another, once. A final state notes when the event was closed and by whom.
     *
     * @param event id of the event
     * @param from  the state it must be in
     * @param to    the new state
     * @param by    who ended or cancelled it, null when its time or money ran out
     * @param at    the current time
     * @return true when it was in {@code from} and is now in {@code to}
     */
    public abstract boolean state(int event, EventState from, EventState to, @Nullable String by, LocalDateTime at);

    /**
     * Ends an event at its end, once: only while it is in a state and still has the end the caller read, so an end
     * moved meanwhile, such as by an extension on another node, keeps it running.
     *
     * @param event id of the event
     * @param from  the state it must be in
     * @param ends  the end it must have
     * @param by    what is noted as who closed it: null for an event that ran, {@link ServerEvent#MISSED_BY} for one
     *              that never started
     * @param at    the current time
     * @return true when it is {@link EventState#ENDED} now
     */
    public abstract boolean expire(
            int event, EventState from, LocalDateTime ends, @Nullable String by, LocalDateTime at);

    /**
     * Moves the end of an event that has not closed, once.
     *
     * @param event id of the event
     * @param from  the end it must have
     * @param to    the new end, after its start
     * @return true when it had {@code from}, still waits or runs, and ends at {@code to} now
     */
    public abstract boolean ends(int event, LocalDateTime from, LocalDateTime to);

    /**
     * Sets the budget of a multiplier that has not closed. A budget is never set below what the event spent already,
     * and only an event funded from an account may go without one.
     *
     * @param event  id of the event
     * @param budget the new budget, null for none
     * @return true when the budget is set
     */
    public abstract boolean budget(int event, @Nullable Double budget);
}
