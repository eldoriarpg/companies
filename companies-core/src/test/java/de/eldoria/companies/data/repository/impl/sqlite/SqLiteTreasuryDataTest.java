/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.data.wrapper.treasury.TreasuryEntry;
import de.eldoria.companies.data.wrapper.treasury.TreasuryLogEntry;
import de.eldoria.companies.util.TestExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

class SqLiteTreasuryDataTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 1, 12, 0, 0);

    private final UUID alice = UUID.randomUUID();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private SqLiteTreasuryData treasury;
    private int company;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(8);
        treasury = new SqLiteTreasuryData(executor);
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

    private static TreasuryEntry entry(TreasuryKind kind, LocalDateTime created) {
        return new TreasuryEntry(kind, null, null, null, created);
    }

    private Optional<Long> credit(long cents) {
        return treasury.submitCredit(company, cents, entry(TreasuryKind.DEPOSIT, NOW))
                .join();
    }

    private List<TreasuryLogEntry> log() {
        return treasury.retrieveLog(company, 1, 100).join();
    }

    @Test
    void aCompanyWithoutRowHasAnEmptyTreasury() {
        var read = treasury.retrieveTreasury(company).join();

        Assertions.assertEquals(company, read.company());
        Assertions.assertEquals(0, read.cents());
        Assertions.assertEquals(0, read.payoutShare());
        Assertions.assertEquals(0, SqLiteTestDatabase.count("company_treasury", "TRUE"));
    }

    @Test
    void creditsCreateTheRowAndAddUp() {
        Assertions.assertEquals(Optional.of(1050L), credit(1050));
        Assertions.assertEquals(Optional.of(1300L), credit(250));

        var read = treasury.retrieveTreasury(company).join();
        Assertions.assertEquals(1300, read.cents());
        Assertions.assertEquals(new BigDecimal("13.00"), read.balance());
        var log = log();
        Assertions.assertEquals(2, log.size());
        Assertions.assertEquals(250, log.getFirst().amount());
        Assertions.assertEquals(1300, log.getFirst().balance());
        Assertions.assertEquals(TreasuryKind.DEPOSIT, log.getFirst().kind());
    }

    @Test
    void creditsOfNothingAreRefused() {
        Assertions.assertTrue(credit(0).isEmpty());
        Assertions.assertTrue(credit(-5).isEmpty());
        Assertions.assertEquals(0, SqLiteTestDatabase.count("treasury_log", "TRUE"));
    }

    @Test
    void payoutSharesRecordTheOrderWithoutActor() {
        treasury.submitCredit(company, 99, new TreasuryEntry(TreasuryKind.PAYOUT_SHARE, null, 42, null, NOW))
                .join();

        var logged = log().getFirst();
        Assertions.assertEquals(Optional.of(42), logged.orderId());
        Assertions.assertTrue(logged.actorId().isEmpty());
        Assertions.assertEquals(NOW, logged.created());
    }

    @Test
    void debitsNeedACoveringBalance() {
        credit(500);
        var withdraw = TreasuryEntry.by(TreasuryKind.WITHDRAW, alice, NOW);

        Assertions.assertTrue(
                treasury.submitDebit(company, 501, withdraw).join().isEmpty());
        Assertions.assertEquals(
                Optional.of(200L), treasury.submitDebit(company, 300, withdraw).join());
        Assertions.assertEquals(
                Optional.of(0L), treasury.submitDebit(company, 200, withdraw).join());
        Assertions.assertTrue(treasury.submitDebit(company, 1, withdraw).join().isEmpty());

        var log = log();
        Assertions.assertEquals(3, log.size());
        Assertions.assertEquals(-200, log.getFirst().amount());
        Assertions.assertEquals(alice, log.getFirst().actorId().orElseThrow());
    }

    @Test
    void aCompanyWithoutRowCannotBeDebited() {
        Assertions.assertTrue(treasury.submitDebit(company, 1, entry(TreasuryKind.WITHDRAW, NOW))
                .join()
                .isEmpty());
        Assertions.assertEquals(0, SqLiteTestDatabase.count("treasury_log", "TRUE"));
    }

    @Test
    void concurrentDebitsNeverOverdraw() {
        credit(1000);
        List<CompletableFuture<Optional<Long>>> debits = new ArrayList<>();
        for (var i = 0; i < 20; i++) {
            debits.add(treasury.submitDebit(company, 100, entry(TreasuryKind.WITHDRAW, NOW)));
        }

        var succeeded = debits.stream()
                .map(CompletableFuture::join)
                .filter(Optional::isPresent)
                .count();

        Assertions.assertEquals(10, succeeded);
        Assertions.assertEquals(0, treasury.retrieveTreasury(company).join().cents());
        Assertions.assertEquals(10, SqLiteTestDatabase.count("treasury_log", "kind = 1"));
        Assertions.assertEquals(0, SqLiteTestDatabase.count("treasury_log", "balance < 0"));
    }

    @Test
    void shareChangesAreStoredAndLogged() {
        Assertions.assertTrue(treasury.submitShare(company, 15, alice, NOW).join());
        credit(100);
        Assertions.assertTrue(treasury.submitShare(company, 20, alice, NOW).join());

        var read = treasury.retrieveTreasury(company).join();
        Assertions.assertEquals(20, read.payoutShare());
        Assertions.assertEquals(100, read.cents());
        var changes = log().stream()
                .filter(logged -> logged.kind() == TreasuryKind.SHARE_CHANGED)
                .toList();
        Assertions.assertEquals(2, changes.size());
        Assertions.assertEquals("20", changes.getFirst().note());
        Assertions.assertEquals(0, changes.getFirst().amount());
        Assertions.assertEquals(100, changes.getFirst().balance());
    }

    @Test
    void drainEmptiesTheTreasuryOnce() {
        credit(4321);

        Assertions.assertEquals(
                Optional.of(4321L),
                treasury.submitDrain(company, entry(TreasuryKind.DISBAND, NOW)).join());
        Assertions.assertEquals(
                Optional.of(0L),
                treasury.submitDrain(company, entry(TreasuryKind.DISBAND, NOW)).join());

        Assertions.assertEquals(0, treasury.retrieveTreasury(company).join().cents());
        var drained = log().getFirst();
        Assertions.assertEquals(TreasuryKind.DISBAND, drained.kind());
        Assertions.assertEquals(-4321, drained.amount());
        Assertions.assertEquals(0, drained.balance());
        Assertions.assertEquals(2, log().size());
    }

    @Test
    void drainingAnUnusedTreasuryReturnsZero() {
        Assertions.assertEquals(
                Optional.of(0L),
                treasury.submitDrain(company, entry(TreasuryKind.DISBAND, NOW)).join());
        Assertions.assertEquals(0, SqLiteTestDatabase.count("treasury_log", "TRUE"));
    }

    @Test
    void logIsPagedNewestFirst() {
        for (var i = 1; i <= 5; i++) {
            treasury.submitCredit(company, i, entry(TreasuryKind.DEPOSIT, NOW.plusMinutes(i)))
                    .join();
        }

        var first = treasury.retrieveLog(company, 1, 2).join();
        var third = treasury.retrieveLog(company, 3, 2).join();

        Assertions.assertEquals(
                List.of(5L, 4L), first.stream().map(TreasuryLogEntry::amount).toList());
        Assertions.assertEquals(
                List.of(1L), third.stream().map(TreasuryLogEntry::amount).toList());
    }

    @Test
    void unknownKindsAreSkipped() {
        credit(10);
        query("UPDATE treasury_log SET kind = 99").single(call()).update();

        Assertions.assertTrue(log().isEmpty());
    }

    @Test
    void purgeDeletesOldLogEntriesOnly() {
        treasury.submitCredit(company, 1, entry(TreasuryKind.DEPOSIT, NOW.minusDays(400)))
                .join();
        treasury.submitCredit(company, 2, entry(TreasuryKind.DEPOSIT, NOW)).join();

        Assertions.assertEquals(1, treasury.submitLogPurge(NOW.minusDays(365)).join());
        Assertions.assertEquals(
                List.of(2L), log().stream().map(TreasuryLogEntry::amount).toList());
        Assertions.assertEquals(3, treasury.retrieveTreasury(company).join().cents());
    }
}
