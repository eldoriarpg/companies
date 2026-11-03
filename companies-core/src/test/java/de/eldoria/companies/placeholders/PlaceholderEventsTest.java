/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyAreasChangeEvent;
import de.eldoria.companies.util.Times;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The placeholder caches of this node follow its own events.
 */
class PlaceholderEventsTest {
    @Test
    void changedAreasReloadTheCompany() {
        var loads = new AtomicInteger();
        var companies = new CompanyPlaceholderCache(
                id -> {
                    loads.incrementAndGet();
                    return CompletableFuture.completedFuture(Optional.empty());
                },
                10,
                () -> Duration.ofMinutes(5));
        var events = new PlaceholderEvents(
                new PlayerPlaceholderCache(
                        uuid -> CompletableFuture.completedFuture(Optional.empty()), 10, () -> Duration.ofMinutes(5)),
                companies,
                new OfferPlaceholderCache(() -> CompletableFuture.completedFuture(List.of())));
        companies.get(7);

        events.onAreas(new CompanyAreasChangeEvent(
                new CompanyProfile(7, "Miners", Times.now(), 1, CompanyDetails.NONE, List.of()),
                CompanyAreasChangeEvent.Change.AREAS));

        assertEquals(2, loads.get());
    }
}
