/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventFundingTest {
    private static final String BANK = "Bank";
    private static final Duration SHORT = Duration.ofMillis(50);

    private final Map<String, Double> balances = new ConcurrentHashMap<>(Map.of(BANK, 100.0));
    private final List<String> deposits = new CopyOnWriteArrayList<>();
    private final List<Runnable> queued = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Logger logger = quiet();

    private final EventPayouts.Funding direct = new EventPayouts.Funding() {
        @Override
        public double balance(String account) {
            return balances.getOrDefault(account, 0.0);
        }

        @Override
        public boolean withdraw(String account, double amount) {
            var balance = balances.getOrDefault(account, 0.0);
            if (balance < amount) return false;
            balances.put(account, balance - amount);
            return true;
        }

        @Override
        public void deposit(String account, double amount) {
            deposits.add(Thread.currentThread().getName());
            balances.merge(account, amount, Double::sum);
        }
    };

    @Test
    void aMainThreadThatAnswersRunsTheCalls() {
        var funding = EventPayouts.Funding.onMainThread(direct, Runnable::run, running::get, SHORT, logger);

        assertEquals(100.0, funding.balance(BANK));
        assertTrue(funding.withdraw(BANK, 40.0));
        assertEquals(60.0, balances.get(BANK));
    }

    @Test
    void aBalanceTheMainThreadDoesNotReadInTimeFails() {
        var funding = EventPayouts.Funding.onMainThread(direct, queued::add, running::get, SHORT, logger);

        assertThrows(IllegalStateException.class, () -> funding.balance(BANK));
    }

    @Test
    void aWithdrawalThatRunsAfterItsTimeoutGivesItsMoneyBack() {
        var funding = EventPayouts.Funding.onMainThread(direct, queued::add, running::get, SHORT, logger);

        assertThrows(IllegalStateException.class, () -> funding.withdraw(BANK, 40.0));
        queued.forEach(Runnable::run);

        assertEquals(100.0, balances.get(BANK));
        assertEquals(1, deposits.size());
    }

    @Test
    void moneyGivenBackWhileThePluginStopsIsDepositedOnTheCallingThread() {
        Executor dropping = queued::add;
        var funding = EventPayouts.Funding.onMainThread(direct, dropping, running::get, SHORT, logger);
        running.set(false);

        funding.deposit(BANK, 25.0);

        assertTrue(queued.isEmpty());
        assertEquals(List.of(Thread.currentThread().getName()), deposits);
        assertEquals(125.0, balances.get(BANK));
    }

    @Test
    void moneyGivenBackWhileThePluginRunsGoesToTheMainThread() {
        var funding = EventPayouts.Funding.onMainThread(direct, queued::add, running::get, SHORT, logger);

        funding.deposit(BANK, 25.0);

        assertEquals(1, queued.size());
        assertTrue(deposits.isEmpty());
        queued.forEach(Runnable::run);
        assertEquals(125.0, balances.get(BANK));
    }

    private static Logger quiet() {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }
}
