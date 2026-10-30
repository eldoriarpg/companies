/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.CreateEventResult;
import de.eldoria.companies.api.write.EventChange;
import de.eldoria.companies.api.write.EventRequest;
import de.eldoria.companies.components.event.IServerEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Reads, creates and ends server events: time-limited tax reductions, reward multipliers and fee reductions.
 * <p>
 * Events are shared by every server of a network. The primary server starts and ends them at their times; a change
 * made here reaches the other servers at once when they share a node bus, otherwise within half a minute. Every
 * future completes off the main thread; see {@link CompaniesApi} for how to get back onto it. Writes need the economy
 * and complete exceptionally with an {@link ApiNotReadyException} before it is set up.
 */
public interface ServerEventApi {
    /**
     * The events that wait for their start or run now.
     *
     * @return future of the events, by start
     */
    CompletableFuture<List<? extends IServerEvent>> live();

    /**
     * The newest events, whatever their state.
     *
     * @param limit how many to read, at least 1
     * @return future of the events, newest first
     * @throws IllegalArgumentException when the limit is below 1
     */
    CompletableFuture<List<? extends IServerEvent>> recent(int limit);

    /**
     * One event.
     *
     * @param id id of the event
     * @return future of the event, empty when there is none with this id
     */
    CompletableFuture<Optional<? extends IServerEvent>> event(int id);

    /**
     * Creates an event that waits for its start. It is announced when it starts like an event an admin created, and
     * {@code <plugin>@api} is noted as who created it.
     *
     * @param plugin  the plugin creating it
     * @param request the event
     * @return future of the created event or why it was not created
     */
    CompletableFuture<CreateEventResult> create(Plugin plugin, EventRequest request);

    /**
     * Ends a running event now. One that has not started is refused; cancel it instead.
     *
     * @param plugin the plugin ending it, noted as {@code <plugin>@api}
     * @param id     id of the event
     * @return future of what came of it
     */
    CompletableFuture<EventChange> end(Plugin plugin, int id);

    /**
     * Cancels an event that waits or runs. A running one is announced as cancelled; a waiting one is withdrawn
     * without an announcement.
     *
     * @param plugin the plugin cancelling it, noted as {@code <plugin>@api}
     * @param id     id of the event
     * @return future of what came of it
     */
    CompletableFuture<EventChange> cancel(Plugin plugin, int id);
}
