/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import de.eldoria.companies.api.write.CancelOrderResult;
import de.eldoria.companies.api.write.DisbandResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResetMeTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final ResetMe.Company OWNED = new ResetMe.Company(7, "Acme", true, 3);
    private static final ResetMe.Company JOINED = new ResetMe.Company(8, "Stonebridge", false, 4);

    private final Fake writes = new Fake();
    private final MovingClock clock = new MovingClock();
    private final ResetMe resetMe = new ResetMe(writes, clock, Duration.ofMinutes(10));

    @Test
    void asksFirstThenDisbandsAndCancelsOnConfirmation() {
        writes.company = Optional.of(OWNED);
        writes.open.addAll(List.of(1, 2));
        writes.taken = 1;

        var ask = assertInstanceOf(ResetMe.Step.Ask.class, resetMe.request(PLAYER));
        assertEquals(new ResetMe.Plan(Optional.of(OWNED), List.of(1, 2), 1), ask.plan());
        assertTrue(writes.cancelled.isEmpty());

        clock.advance(Duration.ofSeconds(10));
        var done = assertInstanceOf(ResetMe.Step.Done.class, resetMe.request(PLAYER));

        assertEquals(new ResetMe.Report(Optional.of(OWNED), Optional.empty(), 2, 1, Optional.empty()), done.report());
        assertEquals(List.of(1, 2), writes.cancelled);
        assertEquals(List.of(7), writes.disbanded);
    }

    @Test
    void aLateConfirmationAsksAgain() {
        writes.open.add(1);

        assertInstanceOf(ResetMe.Step.Ask.class, resetMe.request(PLAYER));
        clock.advance(ResetMe.CONFIRM);
        assertInstanceOf(ResetMe.Step.Ask.class, resetMe.request(PLAYER));

        assertTrue(writes.cancelled.isEmpty());
    }

    @Test
    void aCompanyOwnedBySomeoneElseStays() {
        writes.company = Optional.of(JOINED);
        writes.open.add(1);

        resetMe.request(PLAYER);
        var done = assertInstanceOf(ResetMe.Step.Done.class, resetMe.request(PLAYER));

        assertEquals(Optional.of(JOINED), done.report().member());
        assertTrue(done.report().disbanded().isEmpty());
        assertTrue(writes.disbanded.isEmpty());
    }

    @Test
    void nothingToResetNamesWhatStays() {
        writes.company = Optional.of(JOINED);
        writes.taken = 2;

        var nothing = assertInstanceOf(ResetMe.Step.Nothing.class, resetMe.request(PLAYER));

        assertEquals(new ResetMe.Plan(Optional.of(JOINED), List.of(), 2), nothing.plan());
        assertInstanceOf(ResetMe.Step.Nothing.class, resetMe.request(PLAYER));
    }

    @Test
    void anOrderTakenMeanwhileStays() {
        writes.open.addAll(List.of(1, 2));
        writes.claimed.add(2);

        resetMe.request(PLAYER);
        var done = assertInstanceOf(ResetMe.Step.Done.class, resetMe.request(PLAYER));

        assertEquals(1, done.report().cancelled());
        assertEquals(1, done.report().taken());
    }

    @Test
    void theNextResetWaitsForTheCooldown() {
        writes.open.add(1);
        resetMe.request(PLAYER);
        resetMe.request(PLAYER);

        clock.advance(Duration.ofMinutes(4));
        var wait = assertInstanceOf(ResetMe.Step.Wait.class, resetMe.request(PLAYER));
        assertEquals(Duration.ofMinutes(6), wait.left());

        clock.advance(Duration.ofMinutes(6));
        assertInstanceOf(ResetMe.Step.Ask.class, resetMe.request(PLAYER));
    }

    @Test
    void aFailedDisbandNeedsNoCooldown() {
        writes.company = Optional.of(OWNED);
        writes.disband = DisbandResult.FAILED;

        resetMe.request(PLAYER);
        var done = assertInstanceOf(ResetMe.Step.Done.class, resetMe.request(PLAYER));

        assertEquals(Optional.of(OWNED), done.report().kept());
        assertInstanceOf(ResetMe.Step.Ask.class, resetMe.request(PLAYER));
    }

    @Test
    void everyPlayerConfirmsForThemselves() {
        writes.open.add(1);

        resetMe.request(PLAYER);
        assertInstanceOf(ResetMe.Step.Ask.class, resetMe.request(UUID.randomUUID()));
        assertInstanceOf(ResetMe.Step.Done.class, resetMe.request(PLAYER));
    }

    @Test
    void aConfirmationForAnotherCompanyAsksAgain() {
        writes.open.add(1);
        resetMe.request(PLAYER);
        writes.company = Optional.of(OWNED);

        var ask = assertInstanceOf(ResetMe.Step.Ask.class, resetMe.request(PLAYER));

        assertEquals(Optional.of(OWNED), ask.plan().company());
        assertTrue(writes.cancelled.isEmpty());
        assertInstanceOf(ResetMe.Step.Done.class, resetMe.request(PLAYER));
        assertEquals(List.of(7), writes.disbanded);
    }

    @Test
    void aResetThatBrokeOffNeedsNoCooldown() {
        writes.open.add(1);
        writes.broken = true;
        resetMe.request(PLAYER);

        assertThrows(IllegalStateException.class, () -> resetMe.request(PLAYER));

        writes.broken = false;
        assertInstanceOf(ResetMe.Step.Ask.class, resetMe.request(PLAYER));
    }

    private static final class Fake implements ResetMe.Writes {
        private Optional<ResetMe.Company> company = Optional.empty();
        private final List<Integer> open = new ArrayList<>();
        private final List<Integer> claimed = new ArrayList<>();
        private int taken;
        private boolean broken;
        private DisbandResult disband = DisbandResult.DISBANDED;
        private final List<Integer> cancelled = new ArrayList<>();
        private final List<Integer> disbanded = new ArrayList<>();

        @Override
        public Optional<ResetMe.Company> company(UUID player) {
            return company;
        }

        @Override
        public List<Integer> openOrders(UUID player) {
            return List.copyOf(open);
        }

        @Override
        public int takenOrders(UUID player) {
            return taken;
        }

        @Override
        public DisbandResult disband(int id, UUID player) {
            disbanded.add(id);
            return disband;
        }

        @Override
        public CancelOrderResult cancel(int order, UUID player) {
            if (broken) throw new IllegalStateException("The database does not answer");
            if (claimed.contains(order)) return CancelOrderResult.CLAIMED;
            cancelled.add(order);
            return CancelOrderResult.CANCELLED;
        }
    }

    private static final class MovingClock extends Clock {
        private Instant now = Instant.parse("2026-11-04T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
