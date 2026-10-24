/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.core.updater.SqlVersion;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.alert.AlertResult;
import de.eldoria.companies.data.wrapper.treasury.TreasuryEntry;
import de.eldoria.companies.orders.query.OrderFilter;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Upgrades a database with a company, an order and a payout from the previous version and checks that the treasury,
 * repeating order and alert tables work and the existing rows got their new columns.
 */
class SqLiteEconomyMigrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 12, 0, 0);

    private final UUID owner = UUID.randomUUID();

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
        SqLiteTestDatabase.create(directory, new SqlVersion(1, 6));
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
        query("INSERT INTO order_states(id, state, last_update) VALUES (?, ?, '2026-07-01 10:00:00')")
                .single(call().bind(order).bind(OrderState.UNCLAIMED.stateId()))
                .insert();
        query("INSERT INTO order_payouts(order_id, company_id, worker_uuid, items, gross, tax, net, paid, created)"
                        + " VALUES (?, ?, ?, 4, 40.0, 4.0, 36.0, 1, '2026-07-01 10:00:00')")
                .single(call().bind(order).bind(company).bind(UUID.randomUUID(), UUID_BYTES))
                .insert();

        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
    }

    @Test
    void theNewTablesExist() {
        Assertions.assertEquals(
                4,
                SqLiteTestDatabase.count(
                        "sqlite_master",
                        "type = 'table' AND name IN ('company_treasury', 'treasury_log', 'order_series',"
                                + " 'order_alerts')"));
        Assertions.assertEquals(
                1, SqLiteTestDatabase.count("sqlite_master", "type = 'index' AND name = 'orders_series_index'"));
    }

    @Test
    void existingOrdersAreNoCopies() {
        Assertions.assertEquals(1, SqLiteTestDatabase.count("orders", "series_id IS NULL"));

        var read = new SqLiteOrderData(executor, SqLiteTestDatabase.mapper())
                .retrieveOrderById(order)
                .join()
                .orElseThrow();

        Assertions.assertTrue(read.seriesId().isEmpty());
    }

    @Test
    void existingPayoutsHaveNoShare() {
        Assertions.assertEquals(1, SqLiteTestDatabase.count("order_payouts", "share = 0 AND net = 36.0"));
    }

    @Test
    void theNewRepositoriesWorkOnTheUpgradedDatabase() {
        var treasury = new SqLiteTreasuryData(executor);
        var alerts = new SqLiteAlertData(SqLiteTestDatabase.mapper(), executor);

        Assertions.assertEquals(
                Optional.of(250L),
                treasury.submitCredit(company, 250, new TreasuryEntry(TreasuryKind.DEPOSIT, owner, null, null, NOW))
                        .join());
        Assertions.assertEquals(
                AlertResult.CREATED,
                alerts.submitAlert(company, "all", OrderFilter.ALL, owner, 3, NOW)
                        .join());
        Assertions.assertEquals(
                OrderFilter.ALL, alerts.retrieveEnabled().join().getFirst().filter());
    }
}
