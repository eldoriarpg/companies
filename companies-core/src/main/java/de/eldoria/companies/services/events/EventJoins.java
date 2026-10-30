/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.configuration.elements.events.EventSettings;
import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tells players who join while server events run about them.
 * <p>
 * The first time a player joins while an event runs, the event goes into the player's inbox, which also shows it at
 * once; this is noted per event and player in the database, so it happens once in the whole network. On every later
 * join while it runs, the player is told in chat instead, while {@code joinMessage} of {@code events.yml} is on.
 */
public final class EventJoins implements Listener {
    private final ActiveEvents active;
    private final AServerEventData data;
    private final Supplier<EventSettings> settings;
    private final Notices notices;
    private final Executor executor;
    private final Clock clock;
    private final Logger logger;

    /**
     * @param active   the events of this node
     * @param data     event repository, noting whom an event was told to
     * @param settings the current {@code events.yml}
     * @param notices  tells a player
     * @param executor reads the events off the main thread
     * @param clock    clock giving the current time in UTC
     * @param logger   reports failures
     */
    public EventJoins(
            ActiveEvents active,
            AServerEventData data,
            Supplier<EventSettings> settings,
            Notices notices,
            Executor executor,
            Clock clock,
            Logger logger) {
        this.active = active;
        this.data = data;
        this.settings = settings;
        this.notices = notices;
        this.executor = executor;
        this.clock = clock;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer().getUniqueId();
        try {
            executor.execute(() -> {
                try {
                    joined(player);
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "Could not tell a joining player about the server events", e);
                }
            });
        } catch (RejectedExecutionException e) {
            logger.log(Level.FINE, "Not telling a joining player about the server events while the plugin stops", e);
        }
    }

    /**
     * Tells a player who joined about the events running now.
     *
     * @param player the player
     */
    public void joined(UUID player) {
        var now = LocalDateTime.now(clock);
        var running = active.activeAt(now);
        if (running.isEmpty()) return;
        List<ServerEvent> told = new ArrayList<>();
        for (var event : running) {
            if (data.notice(event.id(), player, now)) {
                notices.inbox(player, event);
            } else {
                told.add(event);
            }
        }
        if (!told.isEmpty() && settings.get().joinMessage()) notices.message(player, told);
    }

    /**
     * How a player is told.
     */
    public interface Notices {
        /**
         * Puts an event into the inbox of a player, which shows it when the player is online.
         *
         * @param player the player
         * @param event  the event
         */
        void inbox(UUID player, ServerEvent event);

        /**
         * Tells a player in chat about events.
         *
         * @param player the player
         * @param events the events
         */
        void message(UUID player, List<ServerEvent> events);
    }
}
