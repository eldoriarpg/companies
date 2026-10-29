/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.util.Times;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OfferPlaceholdersTest {
    private static final LocalDateTime ENDS = LocalDateTime.of(2026, 11, 2, 18, 30);

    private List<EventOffer> running = List.of();
    private OfferPlaceholderCache offers;
    private PlaceholderRegistry registry;

    @BeforeEach
    void setUp() {
        Times.useZone(() -> ZoneOffset.UTC);
        offers = new OfferPlaceholderCache(() -> CompletableFuture.completedFuture(running));
        var players = new PlayerPlaceholderCache(
                uuid -> CompletableFuture.completedFuture(Optional.empty()), 10, () -> Duration.ofMinutes(5));
        var companies = new CompanyPlaceholderCache(
                id -> CompletableFuture.completedFuture(Optional.empty()), 10, () -> Duration.ofMinutes(5));
        var leaderboards = new LeaderboardCache((order, range) -> CompletableFuture.completedFuture(List.of()), 10);
        registry = new PlaceholderRegistry(
                players,
                companies,
                leaderboards,
                offers,
                amount -> String.format(Locale.ROOT, "$%.2f", amount),
                key -> "t:" + key,
                () -> "...");
    }

    @AfterEach
    void tearDown() {
        Times.useZone(Times::machineZone);
    }

    @Test
    void offersReadAsLoadingUntilTheFirstRefresh() {
        assertEquals("...", registry.resolve(null, "offer_active_count"));
        assertEquals("...", registry.resolve(null, "offer_1_name"));
    }

    @Test
    void theRunningOffersAreReadByTheirPlace() {
        running = List.of(
                offer(1, "Harbour logs", OfferState.ACTIVE, 250, 1000),
                offer(2, "Waiting", OfferState.SCHEDULED, 0, 100),
                offer(3, "Race", OfferState.ACTIVE, 10, 20));
        offers.refresh().join();

        assertEquals("2", registry.resolve(null, "offer_active_count"));
        assertEquals("Harbour logs", registry.resolve(null, "offer_1_name"));
        assertEquals("25", registry.resolve(null, "offer_1_progress"));
        assertEquals("t:offers.kind.goal", registry.resolve(null, "OFFER_1_KIND"));
        assertEquals("2026-11-02 18:30", registry.resolve(null, "offer_1_ends"));
        assertEquals("OAK_LOG", registry.resolve(null, "offer_1_item"));
        assertEquals("Race", registry.resolve(null, "offer_2_name"));
        assertEquals("3", registry.resolve(null, "offer_2_id"));
        assertEquals("", registry.resolve(null, "offer_3_name"));
        assertNull(registry.resolve(null, "offer_1_colour"));
        assertNull(registry.resolve(null, "offer_x_name"));
    }

    private static EventOffer offer(int id, String name, OfferState state, int delivered, int amount) {
        var goal = EventOffer.goal(
                name,
                OfferLine.material("OAK_LOG"),
                amount,
                1.0,
                100,
                3,
                SourceType.MINTED,
                null,
                amount,
                ENDS.minusDays(3),
                ENDS,
                "Admin",
                ENDS.minusDays(3));
        return new EventOffer(
                id,
                goal.kind(),
                name,
                null,
                goal.line(),
                amount,
                1.0,
                100,
                3,
                SourceType.MINTED,
                null,
                amount,
                0,
                delivered,
                goal.starts(),
                ENDS,
                state,
                "Admin",
                goal.created(),
                null,
                List.of(),
                0,
                null,
                null,
                null,
                null);
    }
}
