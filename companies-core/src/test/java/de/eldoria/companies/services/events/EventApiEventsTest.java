/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.components.event.EventState;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.events.server.ServerEventEndEvent;
import de.eldoria.companies.events.server.ServerEventEndEvent.Reason;
import de.eldoria.companies.events.server.ServerEventStartEvent;
import de.eldoria.companies.nodes.NodeMessage.ServerEventChanged;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EventApiEventsTest {
    private final ServerEvent running = TestEvents.event(1, EventKind.TAX_REDUCTION, 5);

    @Test
    void aStartFiresTheStartEventWithTheServerEvent() {
        var fired = assertInstanceOf(ServerEventStartEvent.class, EventApiEvents.of(running, "ACTIVE"));

        assertSame(running, fired.event());
    }

    @Test
    void anEndNamesWhyTheEventIsOver() {
        assertEquals(Reason.TIME, reason(closed(EventState.ENDED, null), "ENDED"));
        assertEquals(Reason.ENDED, reason(closed(EventState.ENDED, "Nora"), "ENDED"));
        assertEquals(Reason.DEPLETED, reason(closed(EventState.DEPLETED, null), "DEPLETED"));
        assertEquals(Reason.CANCELLED, reason(closed(EventState.CANCELLED, "Plugin@api"), "CANCELLED"));
    }

    @Test
    void missedWithdrawnAndChangedEventsFireNothing() {
        List<Event> fired = new ArrayList<>();
        var events = new EventApiEvents(fired::add);

        events.accept(closed(EventState.ENDED, ServerEvent.MISSED_BY), ServerEventChanged.MISSED);
        events.accept(closed(EventState.CANCELLED, "Nora"), ServerEventChanged.WITHDRAWN);
        events.accept(running, ServerEventChanged.CHANGED);
        events.accept(running, "SCHEDULED");

        assertEquals(List.of(), fired);
        assertNull(EventApiEvents.of(running, ServerEventChanged.MISSED));
    }

    private static Reason reason(ServerEvent event, String change) {
        return assertInstanceOf(ServerEventEndEvent.class, EventApiEvents.of(event, change))
                .reason();
    }

    private ServerEvent closed(EventState state, @Nullable String by) {
        return new ServerEvent(
                running.id(),
                running.name(),
                running.kind(),
                running.value(),
                running.materials(),
                running.levels(),
                running.starts(),
                running.ends(),
                running.funding(),
                running.account(),
                running.budget(),
                running.spent(),
                state,
                running.template(),
                running.createdBy(),
                running.created(),
                running.ends(),
                by);
    }
}
