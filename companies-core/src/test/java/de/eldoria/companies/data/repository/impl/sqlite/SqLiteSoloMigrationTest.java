/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.core.updater.SqlVersion;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.Claimant;
import de.eldoria.companies.data.wrapper.stats.PayoutRecord;
import de.eldoria.companies.util.TestExecutors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

class SqLiteSoloMigrationTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID worker = UUID.randomUUID();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private int company;
    private int order;

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory, new SqlVersion(1, 8));
        company = query("INSERT INTO companies(name) VALUES ('Miners') RETURNING id")
                .single()
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        order = query("INSERT INTO orders(owner_uuid, name) VALUES (?, 'legacy') RETURNING id")
                .single(call().bind(owner, UUID_BYTES))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        query("INSERT INTO order_states(id, state, company) VALUES (?, ?, ?)")
                .single(call().bind(order).bind(OrderState.CLAIMED.stateId()).bind(company))
                .insert();
        query("INSERT INTO order_payouts(order_id, company_id, worker_uuid, items, gross, tax, share, net, paid,"
                        + " created) VALUES (?, ?, ?, 4, 40.0, 4.0, 3.0, 33.0, 1, '2026-07-01 10:00:00')")
                .single(call().bind(order).bind(company).bind(worker, UUID_BYTES))
                .insert();

        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
    }

    @Test
    void existingClaimsStayCompanyClaims() {
        var read = new SqLiteOrderData(executor, SqLiteTestDatabase.mapper())
                .retrieveOrderById(order)
                .join()
                .orElseThrow();

        Assertions.assertEquals(Optional.empty(), read.soloWorker());
        Assertions.assertEquals(Optional.of(new Claimant.Company(company)), read.claimant());
    }

    @Test
    void existingPayoutsAreKept() {
        Assertions.assertEquals(
                1,
                SqLiteTestDatabase.count(
                        "order_payouts", "company_id = " + company + " AND share = 3.0 AND net = 33.0 AND items = 4"));
        var stats = new SqLiteStatsData(executor);
        Assertions.assertEquals(
                4, stats.retrieveMemberStats(company, null).join().get(0).items());
        Assertions.assertEquals(0, stats.retrieveSoloCompleted(worker).join());
    }

    @Test
    void payoutsWithoutACompanyCanBeWritten() {
        var stats = new SqLiteStatsData(executor);

        Assertions.assertTrue(stats.submitPayouts(List.of(new PayoutRecord(
                        order + 1, 0, worker, 2, 20.0, 0.0, 20.0, true, LocalDateTime.of(2026, 7, 2, 10, 0, 0))))
                .join());
        Assertions.assertEquals(1, stats.retrieveSoloCompleted(worker).join());
    }

    @Test
    void theSoloTablesAndIndexesExist() {
        Assertions.assertEquals(
                1, SqLiteTestDatabase.count("sqlite_master", "type = 'table' AND name = 'solo_cooldowns'"));
        Assertions.assertEquals(
                0, SqLiteTestDatabase.count("sqlite_master", "type = 'table' AND name = 'order_payouts_company_only'"));
        Assertions.assertEquals(
                5,
                SqLiteTestDatabase.count(
                        "sqlite_master",
                        "type = 'index' AND name IN ('order_states_solo_worker_index',"
                                + " 'order_payouts_company_created_index', 'order_payouts_company_worker_index',"
                                + " 'order_payouts_created_index', 'order_payouts_worker_index')"));
    }
}
