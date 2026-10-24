/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.alerts;

import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.orders.query.OrderFilter;
import de.eldoria.companies.services.notifications.NotificationType;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import static de.eldoria.companies.services.alerts.AlertFixtures.COMPANY;
import static de.eldoria.companies.services.alerts.AlertFixtures.MANAGER;
import static de.eldoria.companies.services.alerts.AlertFixtures.OWNER;
import static de.eldoria.companies.services.alerts.AlertFixtures.company;
import static de.eldoria.companies.services.alerts.AlertFixtures.order;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertDigestJobTest {
    private final MemoryAlertData alerts = new MemoryAlertData();
    private final FakeCompanies companies = new FakeCompanies();
    private final AlertFixtures.Recorder recorder = new AlertFixtures.Recorder();
    private final MovableClock clock = new MovableClock();
    private final CompanySettings settings = new CompanySettings();
    private AlertMatcher matcher;
    private AlertDigestJob job;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        companies.add(company(COMPANY));
        var notices = new AlertNotices(recorder, AlertFixtures::money);
        var logger = Logger.getAnonymousLogger();
        matcher = new AlertMatcher(alerts, companies, () -> settings, notices, clock, Runnable::run, logger);
        job = new AlertDigestJob(alerts, companies, () -> settings, notices, clock, logger);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void heldBack(int orders) {
        alerts.put(COMPANY, "iron", OrderFilter.ALL.withMaterials(List.of("iron_ingot")));
        matcher.match(order(1, Material.IRON_INGOT, 64, 100.0));
        for (var i = 0; i < orders; i++) matcher.match(order(2 + i, Material.IRON_INGOT, 64, 100.0));
        recorder.sent.clear();
    }

    @Test
    void nothingIsSentWithinTheWindow() {
        heldBack(3);
        clock.advance(Duration.ofMinutes(10));

        job.run();

        assertTrue(recorder.sent.isEmpty());
    }

    @Test
    void theHeldBackMatchesAreSentOnce() {
        heldBack(3);
        clock.advance(Duration.ofMinutes(15));

        job.run();
        clock.advance(Duration.ofMinutes(30));
        job.run();

        assertEquals(List.of(OWNER, MANAGER), recorder.recipients());
        var sent = recorder.sent.getFirst();
        assertEquals(NotificationType.ORDER_ALERT_DIGEST, sent.type());
        assertEquals("iron", sent.arg("alert_name"));
        assertEquals("3", sent.arg("count"));
        assertEquals(0, alerts.get(1).pending());
    }

    @Test
    void theDigestStartsANewWindow() {
        heldBack(2);
        clock.advance(Duration.ofMinutes(15));
        job.run();
        recorder.sent.clear();

        matcher.match(order(9, Material.IRON_INGOT, 64, 100.0));

        assertTrue(recorder.sent.isEmpty());
        assertEquals(1, alerts.get(1).pending());
        assertEquals(LocalDateTime.now(clock), alerts.get(1).lastNotified().orElseThrow());
    }

    @Test
    void anAlertClaimedElsewhereIsNotSentAgain() {
        heldBack(2);
        clock.advance(Duration.ofMinutes(15));
        var due = alerts.retrieveDigestDue(LocalDateTime.now(clock), Duration.ofMinutes(15))
                .join();
        alerts.submitDigestClaim(due.getFirst(), LocalDateTime.now(clock)).join();

        job.run();

        assertTrue(recorder.sent.isEmpty());
    }

    @Test
    void disabledAlertsSendNoDigest() {
        heldBack(2);
        clock.advance(Duration.ofMinutes(15));
        settings.alerts().enabled(false);

        job.run();

        assertTrue(recorder.sent.isEmpty());
    }
}
