/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.DiscordSettings;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.PrizeState;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordOffersTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

    private final List<Embed> posted = new ArrayList<>();

    @Test
    void theOfferEventsArePostedWhileTheyAreListed() {
        var offers = offers("events: [OFFER_STARTED, GOAL_PROGRESS, CONTEST_RESULT]");
        var goal = goal();

        offers.started(goal);
        offers.goalProgress(goal, 50);
        offers.goalProgress(goal, 100);
        offers.contestCancelled(goal);

        assertEquals(
                List.of(
                        "discord.offerStarted",
                        "discord.goalProgress",
                        "discord.goalReached",
                        "discord.contestCancelled"),
                posted.stream().map(Embed::title).toList());
        assertTrue(
                posted.get(1).fields().stream().anyMatch(field -> field.value().equals("50 %")));
    }

    @Test
    void offerEventsThatAreNotListedAreNotPosted() {
        var offers = offers("events: [ORDER_CREATED]");

        offers.started(goal());
        offers.goalProgress(goal(), 25);
        offers.contestClosed(goal(), List.of(), List.of());

        assertTrue(posted.isEmpty());
    }

    @Test
    void aContestResultListsThePlacesWithTheirPaidPrizes() {
        var offers = offers("events: [CONTEST_RESULT]");
        var ranking = List.of(
                new ContestEntry(1, 7, "Iron Works", 300, 300, NOW, null, NOW),
                new ContestEntry(1, 8, "Stone Cutters", 120, 120, NOW, null, NOW));
        var prizes = List.of(
                new ContestPrize(1, 500, 7, "Iron Works", PrizeState.PAID),
                new ContestPrize(2, 250, 8, "Stone Cutters", PrizeState.FAILED));

        offers.contestClosed(goal(), ranking, prizes);

        var ranked = posted.getFirst().fields().getFirst().value();
        assertEquals("1. Iron Works: 300× · $500.0\n2. Stone Cutters: 120×", ranked);
    }

    @Test
    void anOfferOfAServerEventPostsNoStartOfItsOwnOnlyWhenItStartsWithIt() {
        var offers = offers("events: [OFFER_STARTED]");
        var goal = goal();

        offers.started(onEvent(goal, goal.starts().plusSeconds(30)));
        assertTrue(posted.isEmpty());
        offers.started(onEvent(goal, goal.starts().plusHours(3)));
        assertEquals(
                List.of("discord.offerStarted"),
                posted.stream().map(Embed::title).toList());
    }

    private static EventOffer onEvent(EventOffer goal, LocalDateTime started) {
        return new EventOffer(
                goal.id(),
                goal.kind(),
                goal.name(),
                4,
                goal.line(),
                goal.amount(),
                goal.unitPrice(),
                goal.chunkSize(),
                goal.openChunks(),
                goal.source(),
                goal.account(),
                goal.budget(),
                goal.spent(),
                goal.delivered(),
                goal.starts(),
                goal.ends(),
                OfferState.ACTIVE,
                goal.createdBy(),
                goal.created(),
                goal.closed(),
                goal.prizes(),
                goal.held(),
                started,
                goal.settled(),
                goal.refund(),
                goal.refunded());
    }

    private DiscordOffers offers(String yaml) {
        var settings = YAMLMapper.builder()
                .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                .build()
                .readValue(yaml, DiscordSettings.class);
        return new DiscordOffers(
                () -> settings,
                new DiscordEmbeds(key -> key, InstantSource.fixed(Instant.EPOCH)),
                amount -> "$" + amount,
                key -> key,
                posted::add);
    }

    private static EventOffer goal() {
        return EventOffer.goal(
                "Harbour logs",
                OfferLine.material("OAK_LOG"),
                1000,
                1.0,
                100,
                3,
                SourceType.MINTED,
                null,
                1000,
                NOW,
                NOW.plusDays(1),
                "Admin",
                NOW);
    }
}
