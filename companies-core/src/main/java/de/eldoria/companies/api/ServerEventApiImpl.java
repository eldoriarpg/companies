/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.CreateEventResult;
import de.eldoria.companies.api.write.EventChange;
import de.eldoria.companies.api.write.EventProblem;
import de.eldoria.companies.api.write.EventRequest;
import de.eldoria.companies.components.event.IServerEvent;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.services.events.ServerEventService;
import org.bukkit.plugin.Plugin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Reads the events from their repository and changes them through the {@link ServerEventService}, which checks a new
 * event like {@code /companyadmin event create} does and signals every change to the other servers. A plugin is noted
 * as {@code <plugin>@api}: plugin names contain no {@code @} or {@code #}, so it never looks like a player, a dashboard
 * admin or a marker. The money fields of a kind that pays nothing are dropped.
 */
final class ServerEventApiImpl implements ServerEventApi {
    /**
     * The longest part of a plugin's name noted, so that with {@code @api} it fits the 64 characters of who created or
     * closed an event.
     */
    static final int MAX_PLUGIN_NAME = 60;

    private final AServerEventData events;
    private final WriteAccess writes;
    private final Supplier<LocalDateTime> clock;
    private final Executor executor;

    ServerEventApiImpl(AServerEventData events, WriteAccess writes, Supplier<LocalDateTime> clock, Executor executor) {
        this.events = events;
        this.writes = writes;
        this.clock = clock;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<List<? extends IServerEvent>> live() {
        return CompletableFuture.supplyAsync(events::live, executor);
    }

    @Override
    public CompletableFuture<List<? extends IServerEvent>> recent(int limit) {
        if (limit < 1) throw new IllegalArgumentException("The limit must be at least 1, was " + limit);
        return CompletableFuture.supplyAsync(() -> events.events(limit), executor);
    }

    @Override
    public CompletableFuture<Optional<? extends IServerEvent>> event(int id) {
        return CompletableFuture.supplyAsync(() -> events.event(id), executor);
    }

    @Override
    public CompletableFuture<CreateEventResult> create(Plugin plugin, EventRequest request) {
        var by = by(plugin);
        return writes.run(services -> CompletableFuture.supplyAsync(
                () -> created(services.serverEvents(), draft(request, by, clock.get())), executor));
    }

    @Override
    public CompletableFuture<EventChange> end(Plugin plugin, int id) {
        var by = by(plugin);
        return writes.run(services -> CompletableFuture.supplyAsync(
                () -> change(services.serverEvents().end(id, by)), executor));
    }

    @Override
    public CompletableFuture<EventChange> cancel(Plugin plugin, int id) {
        var by = by(plugin);
        return writes.run(services -> CompletableFuture.supplyAsync(
                () -> change(services.serverEvents().cancel(id, by)), executor));
    }

    /**
     * @param plugin a plugin
     * @return how a change of the plugin is noted
     */
    static String by(Plugin plugin) {
        var name = plugin.getName();
        return (name.length() > MAX_PLUGIN_NAME ? name.substring(0, MAX_PLUGIN_NAME) : name) + "@api";
    }

    /**
     * The event of a request, with the money fields only for a kind that pays.
     *
     * @param request the request
     * @param by      who creates it
     * @param now     the current time
     * @return the event, not stored yet
     */
    static ServerEvent draft(EventRequest request, String by, LocalDateTime now) {
        var funded = request.kind().funded();
        MoneySource.SourceType funding = null;
        if (funded) funding = request.minted() ? MoneySource.SourceType.MINTED : MoneySource.SourceType.ACCOUNT;
        return ServerEvent.draft(
                request.name(),
                request.kind(),
                request.value(),
                request.materials(),
                request.levels(),
                request.starts(),
                request.ends(),
                funding,
                funded && !request.minted() ? request.account() : null,
                funded ? request.budget() : null,
                null,
                by,
                now);
    }

    private static CreateEventResult created(ServerEventService service, ServerEvent draft) {
        var created = service.create(draft);
        var problem = created.problem();
        if (problem != null) return new CreateEventResult.Rejected(EventProblem.valueOf(problem.name()));
        return service.event(created.id())
                .<CreateEventResult>map(CreateEventResult.Created::new)
                .orElseGet(() -> new CreateEventResult.Rejected(EventProblem.STORAGE));
    }

    private static EventChange change(ServerEventService.Change change) {
        return EventChange.valueOf(change.name());
    }
}
