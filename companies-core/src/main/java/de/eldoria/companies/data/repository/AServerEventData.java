/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.event.EventPayout;
import de.eldoria.companies.data.wrapper.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
     * The event a template of {@code events.yml} created last, whatever its state, so the primary creates the next one
     * only after it and never twice, also across restarts. An event the primary withdrew since its template was
     * disabled or removed, closed by {@link ServerEvent#TEMPLATE_BY}, does not count, so the template creates that start
     * again when it is enabled again in time.
     *
     * @param template id of the template
     * @return the event with the latest start, empty when the template created none
     */
    public abstract Optional<ServerEvent> latest(String template);

    /**
     * Notes that a player was told about a running event when joining, once per event and player.
     *
     * @param event  id of the event
     * @param player the player
     * @param at     the current time
     * @return true when the player was not noted for the event before and is now
     */
    public abstract boolean notice(int event, UUID player, LocalDateTime at);

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
     * Writes the payout records of a completed order whose workers server events paid extra, and adds what each event
     * paid to its spent money, in one transaction. An event's money is only added while it is
     * {@link EventState#ACTIVE active} and, when it has a budget, while the budget still covers it; the update locks
     * the event's row until the transaction ends, so two nodes completing orders at once never spend more than the
     * budget: the one that comes second sees its update refused, nothing of its transaction is written, and it reads
     * the events again. The events are updated in the order of their ids, so two transactions never wait for each
     * other's rows.
     *
     * @param spend   what each event pays for the order, by event id
     * @param payouts the payout record of every worker of the order, with what each event paid for it
     * @return true when every event could pay and everything is written; false when nothing is written
     */
    public abstract boolean pay(Map<Integer, Double> spend, List<EventPayout> payouts);

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
