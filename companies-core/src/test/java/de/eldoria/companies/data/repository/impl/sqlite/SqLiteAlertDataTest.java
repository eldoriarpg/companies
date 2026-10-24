/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.wrapper.alert.AlertResult;
import de.eldoria.companies.data.wrapper.alert.HitResult;
import de.eldoria.companies.data.wrapper.alert.OrderAlert;
import de.eldoria.companies.orders.query.OrderFilter;
import de.eldoria.companies.util.TestExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.chojo.sadu.queries.api.query.Query.query;

class SqLiteAlertDataTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 1, 10, 0, 0);
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final OrderFilter IRON = OrderFilter.ALL
            .withMaterials(List.of("iron_ingot"))
            .withMaterialMode(true, true)
            .withPrice(10, 500);

    private final UUID creator = UUID.randomUUID();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private SqLiteAlertData alerts;
    private int miners;
    private int farmers;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(8);
        alerts = new SqLiteAlertData(SqLiteTestDatabase.mapper(), executor);
        miners = insertCompany("Miners");
        farmers = insertCompany("Farmers");
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
    }

    private static int insertCompany(String name) {
        return query("INSERT INTO companies(name) VALUES ('" + name + "') RETURNING id")
                .single()
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    private AlertResult create(int company, String name, int max) {
        return alerts.submitAlert(company, name, IRON, creator, max, NOW).join();
    }

    private OrderAlert alert(int company, String name) {
        return alerts.retrieveByCompany(company).join().stream()
                .filter(alert -> alert.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aSavedAlertIsReadBackWithItsFilter() {
        Assertions.assertEquals(AlertResult.CREATED, create(miners, "iron", 3));

        var read = alert(miners, "iron");

        Assertions.assertEquals(IRON, read.filter());
        Assertions.assertEquals(creator, read.creator());
        Assertions.assertTrue(read.enabled());
        Assertions.assertEquals(NOW, read.created());
        Assertions.assertTrue(read.lastNotified().isEmpty());
        Assertions.assertEquals(0, read.pending());
    }

    @Test
    void namesAreUniquePerCompany() {
        Assertions.assertEquals(AlertResult.CREATED, create(miners, "iron", 3));
        Assertions.assertEquals(AlertResult.NAME_TAKEN, create(miners, "iron", 3));
        Assertions.assertEquals(AlertResult.CREATED, create(farmers, "iron", 3));
    }

    @Test
    void theLimitIsEnforced() {
        Assertions.assertEquals(AlertResult.CREATED, create(miners, "a", 2));
        Assertions.assertEquals(AlertResult.CREATED, create(miners, "b", 2));
        Assertions.assertEquals(AlertResult.LIMIT_REACHED, create(miners, "c", 2));
        Assertions.assertEquals(AlertResult.LIMIT_REACHED, create(farmers, "a", 0));
        Assertions.assertEquals(
                List.of("a", "b"),
                alerts.retrieveByCompany(miners).join().stream()
                        .map(OrderAlert::name)
                        .toList());
    }

    @Test
    void concurrentSavesDoNotExceedTheLimit() {
        List<CompletableFuture<AlertResult>> saves = new ArrayList<>();
        for (var i = 0; i < 10; i++) {
            saves.add(alerts.submitAlert(miners, "alert" + i, IRON, creator, 3, NOW));
        }

        var created = saves.stream()
                .map(CompletableFuture::join)
                .filter(result -> result == AlertResult.CREATED)
                .count();

        Assertions.assertEquals(3, created);
        Assertions.assertEquals(3, alerts.retrieveByCompany(miners).join().size());
    }

    @Test
    void anUnknownCompanyCannotSaveAlerts() {
        Assertions.assertEquals(AlertResult.FAILED, create(farmers + 100, "iron", 3));
    }

    @Test
    void alertsCanBeDisabledAndRemoved() {
        create(miners, "iron", 3);
        create(miners, "gold", 3);
        create(farmers, "wheat", 3);

        Assertions.assertTrue(alerts.submitEnabled(miners, "gold", false).join());
        Assertions.assertFalse(alerts.submitEnabled(miners, "missing", false).join());

        Assertions.assertEquals(
                List.of("iron", "wheat"),
                alerts.retrieveEnabled().join().stream().map(OrderAlert::name).toList());
        Assertions.assertFalse(alert(miners, "gold").enabled());

        Assertions.assertTrue(alerts.submitRemoval(miners, "iron").join());
        Assertions.assertFalse(alerts.submitRemoval(miners, "iron").join());
        Assertions.assertEquals(
                List.of("gold"),
                alerts.retrieveByCompany(miners).join().stream()
                        .map(OrderAlert::name)
                        .toList());
    }

    @Test
    void hitsNotifyOncePerWindowAndCountTheRest() {
        create(miners, "iron", 3);
        var id = alert(miners, "iron").id();

        Assertions.assertEquals(
                HitResult.NOTIFY, alerts.submitHit(id, NOW, WINDOW).join());
        Assertions.assertEquals(
                HitResult.SUPPRESSED,
                alerts.submitHit(id, NOW.plusMinutes(5), WINDOW).join());
        Assertions.assertEquals(
                HitResult.SUPPRESSED,
                alerts.submitHit(id, NOW.plusMinutes(14), WINDOW).join());
        Assertions.assertEquals(2, alert(miners, "iron").pending());
        Assertions.assertEquals(Optional.of(NOW), alert(miners, "iron").lastNotified());

        Assertions.assertEquals(
                HitResult.NOTIFY,
                alerts.submitHit(id, NOW.plusMinutes(15), WINDOW).join());
        Assertions.assertEquals(0, alert(miners, "iron").pending());
    }

    @Test
    void concurrentHitsNotifyOnce() {
        create(miners, "iron", 3);
        var id = alert(miners, "iron").id();
        List<CompletableFuture<HitResult>> hits = new ArrayList<>();
        for (var i = 0; i < 6; i++) hits.add(alerts.submitHit(id, NOW, WINDOW));

        var results = hits.stream().map(CompletableFuture::join).toList();

        Assertions.assertEquals(
                1, results.stream().filter(result -> result == HitResult.NOTIFY).count());
        Assertions.assertEquals(5, alert(miners, "iron").pending());
    }

    @Test
    void disabledOrRemovedAlertsAreGone() {
        create(miners, "iron", 3);
        var id = alert(miners, "iron").id();
        alerts.submitEnabled(miners, "iron", false).join();

        Assertions.assertEquals(
                HitResult.GONE, alerts.submitHit(id, NOW, WINDOW).join());
        alerts.submitRemoval(miners, "iron").join();
        Assertions.assertEquals(
                HitResult.GONE, alerts.submitHit(id, NOW, WINDOW).join());
    }

    @Test
    void theDigestIsClaimedOnceAfterTheWindow() {
        create(miners, "iron", 3);
        create(miners, "quiet", 3);
        var id = alert(miners, "iron").id();
        alerts.submitHit(id, NOW, WINDOW).join();
        alerts.submitHit(id, NOW.plusMinutes(1), WINDOW).join();
        alerts.submitHit(id, NOW.plusMinutes(2), WINDOW).join();
        alerts.submitHit(alert(miners, "quiet").id(), NOW, WINDOW).join();

        Assertions.assertTrue(
                alerts.retrieveDigestDue(NOW.plusMinutes(10), WINDOW).join().isEmpty());
        var due = alerts.retrieveDigestDue(NOW.plusMinutes(15), WINDOW).join();
        Assertions.assertEquals(
                List.of("iron"), due.stream().map(OrderAlert::name).toList());

        Assertions.assertEquals(
                2, alerts.submitDigestClaim(due.getFirst(), NOW.plusMinutes(15)).join());
        Assertions.assertEquals(
                0, alerts.submitDigestClaim(due.getFirst(), NOW.plusMinutes(15)).join());
        var claimed = alert(miners, "iron");
        Assertions.assertEquals(0, claimed.pending());
        Assertions.assertEquals(Optional.of(NOW.plusMinutes(15)), claimed.lastNotified());
        Assertions.assertTrue(
                alerts.retrieveDigestDue(NOW.plusMinutes(30), WINDOW).join().isEmpty());
    }
}
