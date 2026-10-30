/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.components.event.EventState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.wrapper.event.ServerEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Builds server events for tests, running from {@link #START} for a day unless told otherwise.
 */
final class TestEvents {
    static final LocalDateTime START = LocalDateTime.of(2026, 10, 30, 12, 0);

    private TestEvents() {}

    static ServerEvent event(int id, EventKind kind, double value) {
        return event(id, kind, value, START, List.of(), List.of());
    }

    static ServerEvent event(
            int id, EventKind kind, double value, LocalDateTime starts, List<String> materials, List<Integer> levels) {
        var funded = kind.funded();
        return new ServerEvent(
                id,
                "Event " + id,
                kind,
                value,
                materials,
                levels,
                starts,
                starts.plusDays(1),
                funded ? SourceType.MINTED : null,
                null,
                funded ? 1000.0 : null,
                0,
                EventState.ACTIVE,
                null,
                "Nora",
                starts,
                null,
                null);
    }

    static ServerEvent withState(ServerEvent event, EventState state) {
        return new ServerEvent(
                event.id(),
                event.name(),
                event.kind(),
                event.value(),
                event.materials(),
                event.levels(),
                event.starts(),
                event.ends(),
                event.funding(),
                event.account(),
                event.budget(),
                event.spent(),
                state,
                event.template(),
                event.createdBy(),
                event.created(),
                event.closed(),
                event.closedBy());
    }
}
