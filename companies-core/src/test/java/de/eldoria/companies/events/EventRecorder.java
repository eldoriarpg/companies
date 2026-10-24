/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Collects the events a test fires, either handed in directly or caught from the plugin manager of a mock server.
 */
public final class EventRecorder implements Consumer<Event> {
    private final List<Event> events = new CopyOnWriteArrayList<>();

    /**
     * Records every event of the given types the server fires.
     *
     * @param plugin plugin owning the listener
     * @param types  event types to record
     * @return the recorder
     */
    @SafeVarargs
    public static EventRecorder listen(Plugin plugin, Class<? extends Event>... types) {
        var recorder = new EventRecorder();
        var listener = new Listener() {};
        for (var type : types) {
            plugin.getServer()
                    .getPluginManager()
                    .registerEvent(
                            type,
                            listener,
                            EventPriority.MONITOR,
                            (ignored, event) -> {
                                if (type.isInstance(event)) recorder.accept(event);
                            },
                            plugin);
        }
        return recorder;
    }

    @Override
    public void accept(Event event) {
        events.add(event);
    }

    /**
     * @param type event type
     * @param <T>  event type
     * @return the recorded events of the type, in firing order
     */
    public <T extends Event> List<T> of(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /**
     * @param type event type
     * @param <T>  event type
     * @return the one recorded event of the type
     * @throws AssertionError when none or several were recorded
     */
    public <T extends Event> T single(Class<T> type) {
        var recorded = of(type);
        if (recorded.size() != 1)
            throw new AssertionError("Expected one " + type.getSimpleName() + ", got " + recorded);
        return recorded.get(0);
    }
}
