/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.alerts;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.orders.query.OrderFilter;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.Times;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static de.eldoria.companies.services.alerts.AlertFixtures.BUYER;
import static de.eldoria.companies.services.alerts.AlertFixtures.COMPANY;
import static de.eldoria.companies.services.alerts.AlertFixtures.MANAGER;
import static de.eldoria.companies.services.alerts.AlertFixtures.OWNER;
import static de.eldoria.companies.services.alerts.AlertFixtures.WORKER;
import static de.eldoria.companies.services.alerts.AlertFixtures.company;
import static de.eldoria.companies.services.alerts.AlertFixtures.order;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertMatcherTest {
    private static final OrderFilter IRON = OrderFilter.ALL.withMaterials(List.of("iron_ingot"));

    private final MemoryAlertData alerts = new MemoryAlertData();
    private final FakeCompanies companies = new FakeCompanies();
    private final AlertFixtures.Recorder recorder = new AlertFixtures.Recorder();
    private final MovableClock clock = new MovableClock();
    private CompanySettings settings;
    private AlertMatcher matcher;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        settings = new CompanySettings();
        settings.levelSettings(1).maxOrders(5);
        settings.levelSettings(1).maxOrderSize(0);
        settings.levelSettings(1).taxPercent(10.0);
        settings.allowMemberOrders(false);
        companies.add(company(COMPANY));
        matcher = new AlertMatcher(
                alerts,
                companies,
                () -> settings,
                new AlertNotices(recorder, AlertFixtures::money),
                clock,
                Runnable::run,
                Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMatchingOrderNotifiesTheMembersWhoManageOrders() {
        alerts.put(COMPANY, "iron", IRON);

        matcher.match(order(11, Material.IRON_INGOT, 64, 100.0));

        assertEquals(List.of(OWNER, MANAGER), recorder.recipients());
        var sent = recorder.sent.getFirst();
        assertEquals(NotificationType.ORDER_ALERT, sent.type());
        assertEquals("iron", sent.arg("alert_name"));
        assertEquals("11 - Supplies", sent.arg("order_name"));
        assertEquals("11", sent.arg("order_id"));
        assertEquals("64 iron_ingot", sent.arg("contents"));
        assertEquals("$100.00", sent.arg("amount"));
        assertEquals("$90.00", sent.arg("net"));
    }

    @Test
    void anOrderThatDoesNotMatchNotifiesNobody() {
        alerts.put(COMPANY, "iron", IRON);

        matcher.match(order(11, Material.GOLD_INGOT, 64, 100.0));

        assertTrue(recorder.sent.isEmpty());
    }

    @Test
    void theOwnerOfTheOrderIsNeverSent() {
        alerts.put(COMPANY, "iron", IRON);

        matcher.match(order(11, Material.IRON_INGOT, 64, 100.0));

        var sent = recorder.sent.getFirst();
        var buyer = BUYER.toString();
        sent.args().values().values().forEach(arg -> assertFalse(arg.value().contains(buyer), arg.value()));
        assertFalse(sent.args().toJson().contains(buyer));
    }

    @Test
    void ordersOfOwnMembersAreHiddenUnlessAllowed() {
        alerts.put(COMPANY, "iron", IRON);

        matcher.match(order(11, "Mine", Material.IRON_INGOT, 64, 100.0, WORKER));
        assertTrue(recorder.sent.isEmpty());

        settings.allowMemberOrders(true);
        matcher.match(order(12, "Mine", Material.IRON_INGOT, 64, 100.0, WORKER));
        assertEquals(2, recorder.sent.size());
    }

    @Test
    void ordersLargerThanTheLevelAllowsAreHidden() {
        alerts.put(COMPANY, "iron", IRON);
        settings.levelSettings(1).maxOrderSize(32);

        matcher.match(order(11, Material.IRON_INGOT, 64, 100.0));

        assertTrue(recorder.sent.isEmpty());
        assertEquals(0, alerts.get(1).pending());
    }

    @Test
    void aCompanyAtItsOrderLimitIsNotNotified() {
        alerts.put(COMPANY, "iron", IRON);
        companies.accepted(COMPANY, 5);

        matcher.match(order(11, Material.IRON_INGOT, 64, 100.0));

        assertTrue(recorder.sent.isEmpty());
    }

    @Test
    void aLevelWithoutAlertsIsNotNotified() {
        alerts.put(COMPANY, "iron", IRON);
        settings.levelSettings(1).maxAlerts(0);

        matcher.match(order(11, Material.IRON_INGOT, 64, 100.0));

        assertTrue(recorder.sent.isEmpty());
    }

    @Test
    void disabledAlertsNotifyNobody() {
        alerts.put(COMPANY, "iron", IRON);
        settings.alerts().enabled(false);

        matcher.match(order(11, Material.IRON_INGOT, 64, 100.0));

        assertTrue(recorder.sent.isEmpty());
    }

    @Test
    void severalAlertsOfOneCompanySendOneNotification() {
        alerts.put(COMPANY, "iron", IRON);
        alerts.put(COMPANY, "cheap", OrderFilter.ALL.withPrice(0, 500));
        alerts.put(COMPANY, "gold", OrderFilter.ALL.withMaterials(List.of("gold_ingot")));

        matcher.match(order(11, Material.IRON_INGOT, 64, 100.0));

        assertEquals(List.of(OWNER, MANAGER), recorder.recipients());
        assertEquals("iron, cheap", recorder.sent.getFirst().arg("alert_name"));
    }

    @Test
    void eachCompanyIsNotifiedAboutItsOwnAlerts() {
        companies.add(company(8));
        alerts.put(COMPANY, "iron", IRON);
        alerts.put(8, "all", OrderFilter.ALL);

        matcher.match(order(11, Material.IRON_INGOT, 64, 100.0));

        assertEquals(4, recorder.sent.size());
        assertEquals("all", recorder.sent.get(2).arg("alert_name"));
    }

    @Test
    void aRateLimitedAlertCountsTheMatchForTheDigest() {
        var alert = alerts.put(COMPANY, "iron", IRON);

        matcher.match(order(11, Material.IRON_INGOT, 64, 100.0));
        clock.advance(Duration.ofMinutes(5));
        matcher.match(order(12, Material.IRON_INGOT, 32, 50.0));
        matcher.match(order(13, Material.IRON_INGOT, 16, 25.0));

        assertEquals(2, recorder.sent.size());
        assertEquals(2, alerts.get(alert.id()).pending());

        clock.advance(Duration.ofMinutes(10));
        matcher.match(order(14, Material.IRON_INGOT, 8, 12.0));

        assertEquals(4, recorder.sent.size());
        assertEquals("14", recorder.sent.getLast().arg("order_id"));
        assertEquals(0, alerts.get(alert.id()).pending());
    }

    @Test
    void onlyAlertsOutsideTheirWindowAreNamed() {
        alerts.put(COMPANY, "iron", IRON);
        matcher.match(order(11, Material.IRON_INGOT, 64, 100.0));
        alerts.put(COMPANY, "cheap", OrderFilter.ALL.withPrice(0, 500));

        matcher.match(order(12, Material.IRON_INGOT, 64, 100.0));

        assertEquals(4, recorder.sent.size());
        assertEquals("cheap", recorder.sent.getLast().arg("alert_name"));
    }

    @Test
    void longOrdersListTheirFirstLines() {
        var lines = IntStream.range(0, 7)
                .mapToObj(i -> new OrderContent(i, new ItemStack(Material.STONE), false, i + 1, 1.0))
                .toList();
        var order =
                new FullOrder(1, BUYER, "Stones", Times.now(), 0, Times.now(), OrderState.UNCLAIMED, 0.0, 0, 0, lines);

        assertEquals("1 stone, 2 stone, 3 stone, 4 stone, 5 stone, …", AlertNotices.contents(order));
    }
}
