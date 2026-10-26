/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings.Competition;
import de.eldoria.companies.data.wrapper.order.DeliveredVolume;
import de.eldoria.companies.data.wrapper.order.PlayerDemand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuessedChoiceTest {
    private final GuessedSettings settings = new GuessedSettings();

    @Test
    void theMaterialPlayersBuyLeastLatelyRanksFirst() {
        var ranking = GuessedChoice.rank(
                settings,
                List.of(
                        new DeliveredVolume("STONE", 5, 1400, 300),
                        new DeliveredVolume("DIRT", 5, 1400, 30),
                        new DeliveredVolume("SAND", 2, 1400, 0)));

        assertEquals(
                List.of("DIRT", "STONE"),
                ranking.stream().map(GuessedChoice.Candidate::material).toList());
        var dirt = ranking.getFirst();
        assertEquals(100.0, dirt.longRate());
        assertEquals(10.0, dirt.recentRate());
        assertEquals(0.1, dirt.score(), 0.000_1);
        assertEquals(280, dirt.typical());
    }

    @Test
    void allowedMaterialsAreCandidatesAndDeniedOnesNever() {
        settings.allow(List.of("sand", "gravel"));
        settings.deny(List.of("stone", "gravel"));

        var ranking = GuessedChoice.rank(
                settings, List.of(new DeliveredVolume("STONE", 5, 100, 10), new DeliveredVolume("SAND", 1, 70, 70)));

        assertEquals(
                List.of("SAND"),
                ranking.stream().map(GuessedChoice.Candidate::material).toList());
    }

    @Test
    void anAllowedMaterialWithoutHistoryHasNoTypicalAmount() {
        settings.allow(List.of("CLAY_BALL"));

        var candidate = GuessedChoice.rank(settings, List.of()).getFirst();

        assertEquals("CLAY_BALL", candidate.material());
        assertEquals(0, candidate.typical());
    }

    @Test
    void eachRepriceRaisesTheFactorUpToItsMaximum() {
        settings.jitter(0);
        settings.factor(0.8);
        settings.repriceStep(0.1);

        assertEquals(8.0, price(0, null).unit());
        assertEquals(9.0, price(1, null).unit());
        assertEquals(10.0, price(3, null).unit());
        assertNull(price(0, null).cap());
    }

    @Test
    void anUnderbiddingOrderPaysClearlyLessThanPlayersAndTheMarket() {
        settings.jitter(0);
        settings.competition(Competition.UNDERBID);

        var belowPlayers = price(0, new PlayerDemand("DIRT", 1, 64, 6.0));
        var belowMarket = price(3, null);

        assertEquals(4.2, belowPlayers.cap());
        assertEquals(4.2, belowPlayers.unit());
        assertEquals(6.0, belowPlayers.lowest());
        assertEquals(7.0, belowMarket.unit());
    }

    @Test
    void anUnderbidCapBelowTheLowestPriceHasNoPrice() {
        settings.competition(Competition.UNDERBID);
        settings.minPrice(8.0);

        assertTrue(GuessedChoice.price(settings, 10.0, 0, null, () -> 0.5).isEmpty());
    }

    @Test
    void aPriceBelowACentPlacesNothing() {
        assertTrue(GuessedChoice.price(settings, 0.001, 0, null, () -> 0.5).isEmpty());
    }

    @Test
    void theAmountIsAShareOfTheTypicalOneInWholeStacks() {
        assertEquals(128, GuessedChoice.amount(settings, 280, 64));
        assertEquals(64, GuessedChoice.amount(settings, 10, 64));
        assertEquals(64, GuessedChoice.amount(settings, 0, 64));
        assertEquals(16, GuessedChoice.amount(settings, 0, 16));
        settings.maxItems(100);
        assertEquals(64, GuessedChoice.amount(settings, 1000, 64));
        settings.maxItems(10);
        assertEquals(10, GuessedChoice.amount(settings, 1000, 64));
    }

    @Test
    void theTargetFollowsTheShareWithinMinimumAndMaximum() {
        settings.share(0.2);
        settings.minOpen(3);
        settings.maxOpen(10);

        assertEquals(3, GuessedChoice.target(settings, 0));
        assertEquals(5, GuessedChoice.target(settings, 20));
        assertEquals(10, GuessedChoice.target(settings, 400));
        settings.share(0);
        assertEquals(3, GuessedChoice.target(settings, 400));
    }

    private GuessedChoice.Price price(int step, PlayerDemand demand) {
        return GuessedChoice.price(settings, 10.0, step, demand, () -> 0.5).orElseThrow();
    }
}
