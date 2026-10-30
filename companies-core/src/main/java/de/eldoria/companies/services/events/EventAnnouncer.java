/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.configuration.elements.events.EventSettings;
import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.order.EventTexts;
import de.eldoria.companies.nodes.NodeMessage.ServerEventChanged;
import de.eldoria.eldoutilities.messages.MessageSender;
import org.bukkit.plugin.Plugin;

import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/**
 * Tells the players of this server when a server event starts and ends.
 * <p>
 * Every node runs one and hears every change through the {@link de.eldoria.companies.nodes.EventNetwork}: while the
 * bus delivers at once, each change reaches every node once, and each node tells its own players. A start is
 * announced, and so are an end, an event that ran out of money and an event cancelled after it started. A created
 * event, a new end or budget, an event cancelled before its start and one whose whole time passed while the primary
 * was down are not. Nothing is announced while {@code broadcast} of {@code events.yml} is off.
 */
public final class EventAnnouncer {
    private final AServerEventData data;
    private final Supplier<EventSettings> settings;
    private final Consumer<Announcement> broadcast;

    /**
     * @param data      event repository, read when a change arrives
     * @param settings  the current {@code events.yml}
     * @param broadcast tells every player of this server
     */
    public EventAnnouncer(AServerEventData data, Supplier<EventSettings> settings, Consumer<Announcement> broadcast) {
        this.data = data;
        this.settings = settings;
        this.broadcast = broadcast;
    }

    /**
     * Announces a change that arrived. Reads the event from the database; call it off the main thread.
     *
     * @param message the change
     */
    public void handle(ServerEventChanged message) {
        var key = key(message.change());
        if (key.isEmpty() || !settings.get().broadcast()) return;
        data.event(message.event()).ifPresent(event -> broadcast.accept(new Announcement(key.get(), event)));
    }

    /**
     * @param change a change of an event, see {@link ServerEventChanged}
     * @return the locale key of its announcement, empty when it is not announced
     */
    public static Optional<String> key(String change) {
        return switch (change) {
            case "ACTIVE" -> Optional.of("events.announce.started");
            case "ENDED" -> Optional.of("events.announce.ended");
            case "DEPLETED" -> Optional.of("events.announce.depleted");
            case "CANCELLED" -> Optional.of("events.announce.cancelled");
            default -> Optional.empty();
        };
    }

    /**
     * Tells every player online on this server, each in their own language, on the main thread.
     *
     * @param plugin     the plugin
     * @param messages   sends the announcement
     * @param money      formats an amount of money
     * @param zone       the server's time zone
     * @param mainThread runs the sending
     * @return the broadcast
     */
    public static Consumer<Announcement> players(
            Plugin plugin,
            MessageSender messages,
            DoubleFunction<String> money,
            Supplier<ZoneId> zone,
            Executor mainThread) {
        return announcement -> mainThread.execute(() -> {
            for (var player : plugin.getServer().getOnlinePlayers()) {
                var values = EventTexts.values(
                        new MenuComponents(messages, player), announcement.event(), money, zone.get());
                messages.sendMessage(player, MenuText.create().locale(announcement.key(), values));
            }
        });
    }

    /**
     * An announcement of an event.
     *
     * @param key   the locale key of the text
     * @param event the event as it is now
     */
    public record Announcement(String key, ServerEvent event) {}
}
