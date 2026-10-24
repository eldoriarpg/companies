/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTreasuryData;
import de.eldoria.companies.data.wrapper.treasury.TreasuryEntry;
import de.eldoria.companies.util.TestExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TreasuryLogRetentionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));

    private final AtomicInteger days = new AtomicInteger(30);

    @TempDir
    Path directory;

    private ExecutorService executor;
    private SqLiteTreasuryData treasury;
    private TreasuryLogRetention retention;
    private int company;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(2);
        treasury = new SqLiteTreasuryData(executor);
        retention = new TreasuryLogRetention(treasury, days::get, CLOCK, Logger.getAnonymousLogger());
        company = query("INSERT INTO companies(name) VALUES ('Miners') RETURNING id")
                .single()
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
    }

    private void deposit(LocalDateTime created) {
        treasury.submitCredit(company, 100, new TreasuryEntry(TreasuryKind.DEPOSIT, null, null, null, created))
                .join();
    }

    @Test
    void entriesOlderThanTheRetentionAreDeletedAndTheBalanceStays() {
        deposit(NOW.minusDays(31));
        deposit(NOW.minusDays(29));

        retention.run();

        assertEquals(1, treasury.retrieveLog(company, 1, 10).join().size());
        assertEquals(200, treasury.retrieveTreasury(company).join().cents());
    }

    @Test
    void zeroDaysKeepsEverything() {
        days.set(0);
        deposit(NOW.minusDays(4000));

        retention.run();

        assertEquals(1, treasury.retrieveLog(company, 1, 10).join().size());
    }
}
