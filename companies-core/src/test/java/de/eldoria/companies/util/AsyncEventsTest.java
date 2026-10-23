/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncEventsTest {
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void asynchronousEventFiredOnTheMainThreadReachesListenersOffIt() throws InterruptedException {
        var received = new CountDownLatch(1);
        var onMainThread = new AtomicBoolean(true);
        plugin.getServer()
                .getPluginManager()
                .registerEvents(
                        new Listener() {
                            @EventHandler
                            public void on(SampleEvent event) {
                                onMainThread.set(Bukkit.isPrimaryThread());
                                received.countDown();
                            }
                        },
                        plugin);

        assertTrue(Bukkit.isPrimaryThread());
        AsyncEvents.fire(plugin, new SampleEvent());

        assertTrue(received.await(5, TimeUnit.SECONDS));
        assertFalse(onMainThread.get());
    }

    /**
     * An event declared asynchronous, like the company events.
     */
    public static class SampleEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        /**
         * Creates the event.
         */
        public SampleEvent() {
            super(true);
        }

        /**
         * @return the handlers of this event
         */
        public static HandlerList getHandlerList() {
            return HANDLERS;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }
    }
}
