/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.commands.companyadmin.OffersCommand;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOfferData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.orders.system.SystemOrderService;
import de.eldoria.companies.util.Times;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moving the end and changing the budget of an offer that runs: refused for closed offers and offers of a server
 * event, the budget never below what was spent and the prizes, and an account contest's raise and cut paid and given
 * back at once.
 */
class OfferEditsTest {
    @TempDir
    Path directory;

    private SqLiteOfferData offers;
    private final Map<String, Double> balances = new HashMap<>();
    private final SystemOrderService.Funding funding = new SystemOrderService.Funding() {
        @Override
        public boolean withdraw(String account, double amount) {
            var balance = balances.getOrDefault(account, 0.0);
            if (balance < amount) return false;
            balances.put(account, balance - amount);
            return true;
        }

        @Override
        public void deposit(String account, double amount) {
            balances.merge(account, amount, Double::sum);
        }
    };

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        offers = new SqLiteOfferData(SqLiteTestDatabase.mapper());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theEndMovesEitherWayAndAnEndInThePastClosesAtTheNextCheck() {
        var now = Times.now().truncatedTo(ChronoUnit.SECONDS);
        var id = goal(now);
        offers.activate(id, 0, now);

        assertEquals(OfferEdits.Outcome.DONE, OfferEdits.end(offers, id, now.plusDays(3), now));
        assertEquals(now.plusDays(3), offers.offer(id).orElseThrow().ends());
        assertEquals(OfferEdits.Outcome.DONE, OfferEdits.end(offers, id, now.minusDays(1), now));
        assertEquals(now, offers.offer(id).orElseThrow().ends());
        assertEquals(
                OfferEdits.Outcome.TOO_HIGH, OfferEdits.end(offers, id, now.plusHours(GoalDraft.MAX_HOURS + 1), now));
        assertEquals(OfferEdits.Outcome.UNKNOWN, OfferEdits.end(offers, id + 1, now, now));
    }

    @Test
    void anOfferThatNeverStartedCanNotEndBeforeItsStart() {
        var now = Times.now().truncatedTo(ChronoUnit.SECONDS);
        var id = goal(now.plusHours(5));

        assertEquals(OfferEdits.Outcome.BEFORE_START, OfferEdits.end(offers, id, now.plusHours(2), now));
        assertEquals(OfferEdits.Outcome.DONE, OfferEdits.end(offers, id, now.plusHours(GoalDraft.MAX_HOURS + 2), now));
        assertEquals(
                OfferEdits.Outcome.TOO_HIGH, OfferEdits.end(offers, id, now.plusHours(GoalDraft.MAX_HOURS + 6), now));
    }

    @Test
    void aRaiseTakenFromTheAccountGoesBackWhenTheOfferChangedMeanwhile() {
        var now = Times.now();
        var id = contest(now, SourceType.ACCOUNT);
        offers.activate(id, 170, now);
        balances.put("Bank", 100.0);
        var racing = new SystemOrderService.Funding() {
            @Override
            public boolean withdraw(String account, double amount) {
                offers.budget(id, 170, 170, 180, 180, 1000);
                return funding.withdraw(account, amount);
            }

            @Override
            public void deposit(String account, double amount) {
                funding.deposit(account, amount);
            }
        };

        assertEquals(OfferEdits.Outcome.CHANGED, OfferEdits.budget(offers, racing, id, 200));
        assertEquals(100.0, balances.get("Bank"), 0.001);
        assertEquals(180.0, offers.offer(id).orElseThrow().budget());
    }

    @Test
    void closedOffersAndOffersOfAServerEventKeepTheirEnd() {
        var now = Times.now();
        var closed = goal(now);
        offers.state(closed, OfferState.SCHEDULED, OfferState.CANCELLED, now);
        var linked = goal(now);
        offers.link(linked, 7);

        assertEquals(OfferEdits.Outcome.CLOSED, OfferEdits.end(offers, closed, now.plusDays(1), now));
        assertEquals(OfferEdits.Outcome.LINKED, OfferEdits.end(offers, linked, now.plusDays(1), now));
        assertEquals(OfferEdits.Outcome.CLOSED, OfferEdits.budget(offers, funding, closed, 500));
    }

    @Test
    void aGoalsBudgetRisesAndFallsAsANumber() {
        var id = goal(Times.now());

        assertEquals(OfferEdits.Outcome.DONE, OfferEdits.budget(offers, funding, id, 250));
        assertEquals(250.0, offers.offer(id).orElseThrow().budget());
        assertEquals(OfferEdits.Outcome.DONE, OfferEdits.budget(offers, funding, id, 40));
        assertEquals(40.0, offers.offer(id).orElseThrow().budget());
        assertEquals(OfferEdits.Outcome.NOT_POSITIVE, OfferEdits.budget(offers, funding, id, 0));
        assertEquals(OfferEdits.Outcome.TOO_HIGH, OfferEdits.budget(offers, funding, id, GoalDraft.MAX_BUDGET * 2));
        assertEquals(OfferEdits.Outcome.TOO_HIGH, OfferEdits.budget(offers, funding, id, Double.NaN));
        assertTrue(balances.isEmpty());
    }

    @Test
    void anAccountContestPaysARaiseAndGetsACutBackAtOnce() {
        var now = Times.now();
        var id = contest(now, SourceType.ACCOUNT);
        offers.activate(id, 170, now);
        balances.put("Bank", 50.0);

        assertEquals(OfferEdits.Outcome.CANNOT_PAY, OfferEdits.budget(offers, funding, id, 300));
        assertEquals(170.0, offers.offer(id).orElseThrow().budget());
        assertEquals(OfferEdits.Outcome.DONE, OfferEdits.budget(offers, funding, id, 200));
        assertEquals(20.0, balances.get("Bank"), 0.001);
        assertEquals(200.0, offers.offer(id).orElseThrow().held());
        assertEquals(OfferEdits.Outcome.DONE, OfferEdits.budget(offers, funding, id, 100));
        assertEquals(120.0, balances.get("Bank"), 0.001);
        assertEquals(100.0, offers.offer(id).orElseThrow().held());
        assertEquals(OfferEdits.Outcome.TOO_LOW, OfferEdits.budget(offers, funding, id, 69));
        assertEquals(70.0, OfferEdits.floor(offers.offer(id).orElseThrow()));
    }

    @Test
    void aContestThatHasNotStartedOrMintsMovesNoMoney() {
        var now = Times.now();
        var waiting = contest(now.plusHours(2), SourceType.ACCOUNT);
        var minted = contest(now, SourceType.MINTED);
        offers.activate(minted, 0, now);

        assertEquals(OfferEdits.Outcome.DONE, OfferEdits.budget(offers, funding, waiting, 500));
        assertEquals(0.0, offers.offer(waiting).orElseThrow().held());
        assertEquals(OfferEdits.Outcome.DONE, OfferEdits.budget(offers, funding, minted, 90));
        assertTrue(balances.isEmpty());
    }

    @Test
    void aContestWhoseBudgetChangedSinceItWasReadDoesNotStart() {
        var now = Times.now();
        var id = contest(now, SourceType.ACCOUNT);

        OfferEdits.budget(offers, funding, id, 500);

        assertFalse(offers.activate(id, 170, now));
        assertTrue(offers.activate(id, 500, now));
    }

    @Test
    void theEndIsReadAsHoursADurationOrADate() {
        var now = LocalDateTime.of(2026, 11, 1, 12, 0);

        assertEquals(now.plusHours(12), OffersCommand.endOf("12", now).orElseThrow());
        assertEquals(now.plusHours(36), OffersCommand.endOf("1d12h", now).orElseThrow());
        assertTrue(OffersCommand.endOf("2026-12-24 18:00", now).isPresent());
        assertTrue(OffersCommand.endOf("soon", now).isEmpty());
    }

    private int goal(LocalDateTime now) {
        return offers.create(EventOffer.goal(
                        "Logs",
                        OfferLine.material("OAK_LOG"),
                        100,
                        1.0,
                        10,
                        1,
                        SourceType.MINTED,
                        null,
                        100,
                        now,
                        now.plusDays(1),
                        "Admin",
                        now))
                .orElseThrow();
    }

    private int contest(LocalDateTime starts, SourceType source) {
        return offers.create(EventOffer.contest(
                        "Race",
                        OfferLine.material("OAK_LOG"),
                        100,
                        1.0,
                        List.of(50.0, 20.0),
                        source,
                        source == SourceType.ACCOUNT ? "Bank" : null,
                        170,
                        starts,
                        starts.plusDays(1),
                        "Admin",
                        Times.now()))
                .orElseThrow();
    }
}
