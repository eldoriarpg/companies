/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.alerts;

import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.data.wrapper.alert.OrderAlert;
import de.eldoria.companies.orders.query.OrderFilter;
import de.eldoria.companies.services.alerts.AlertService.Done;
import de.eldoria.companies.services.alerts.AlertService.Outcome;
import de.eldoria.companies.services.alerts.AlertService.Overview;
import de.eldoria.companies.services.alerts.AlertService.Refused;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;

import static de.eldoria.companies.services.alerts.AlertFixtures.COMPANY;
import static de.eldoria.companies.services.alerts.AlertFixtures.MANAGER;
import static de.eldoria.companies.services.alerts.AlertFixtures.OWNER;
import static de.eldoria.companies.services.alerts.AlertFixtures.WORKER;
import static de.eldoria.companies.services.alerts.AlertFixtures.company;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertServiceTest {
    private static final OrderFilter IRON = OrderFilter.ALL.withMaterials(List.of("iron_ingot"));

    private final MemoryAlertData alerts = new MemoryAlertData();
    private final FakeCompanies companies = new FakeCompanies();
    private final CompanySettings settings = new CompanySettings();
    private ServerMock server;
    private PlayerMock owner;
    private PlayerMock manager;
    private PlayerMock worker;
    private PlayerMock outsider;
    private AlertService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        owner = playerWith(OWNER, "Owner");
        manager = playerWith(MANAGER, "Manager");
        worker = playerWith(WORKER, "Worker");
        outsider = server.addPlayer("Outsider");
        companies.add(company(COMPANY));
        service = new AlertService(alerts, companies, () -> settings, new MovableClock());
    }

    private PlayerMock playerWith(UUID uuid, String name) {
        var player = new PlayerMock(server, name, uuid);
        server.addPlayer(player);
        return player;
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String refusal(Outcome<?> outcome) {
        return assertInstanceOf(Refused.class, outcome).failure().localeKey();
    }

    @Test
    void membersWhoManageOrdersSaveAlerts() {
        assertEquals(
                "iron",
                assertInstanceOf(Done.class, service.add(manager, "iron", IRON).join())
                        .value());
        assertInstanceOf(Done.class, service.add(owner, "iron-2", IRON).join());

        var saved = alerts.retrieveByCompany(COMPANY).join();
        assertEquals(
                List.of("iron", "iron-2"), saved.stream().map(OrderAlert::name).toList());
        assertEquals(IRON, saved.getFirst().filter());
        assertEquals(MANAGER, saved.getFirst().creator());
    }

    @Test
    void membersWithoutThePermissionChangeNothing() {
        alerts.put(COMPANY, "iron", IRON);

        assertEquals(
                "company.alerts.error.permission",
                refusal(service.add(worker, "gold", IRON).join()));
        assertEquals(
                "company.alerts.error.permission",
                refusal(service.remove(worker, "iron").join()));
        assertEquals(
                "company.alerts.error.permission",
                refusal(service.enable(worker, "iron", false).join()));
        assertEquals(1, alerts.retrieveEnabled().join().size());
    }

    @Test
    void everyMemberSeesTheAlerts() {
        alerts.put(COMPANY, "iron", IRON);

        var overview = (Overview)
                assertInstanceOf(Done.class, service.overview(worker).join()).value();

        assertEquals(1, overview.alerts().size());
        assertFalse(overview.mayManage());
        assertTrue(((Overview)
                        assertInstanceOf(Done.class, service.overview(manager).join())
                                .value())
                .mayManage());
        assertInstanceOf(Done.class, service.find(worker, "iron").join());
    }

    @Test
    void playersWithoutCompanyAreRefused() {
        assertEquals("error.noMember", refusal(service.overview(outsider).join()));
        assertEquals(
                "error.noMember", refusal(service.add(outsider, "iron", IRON).join()));
    }

    @Test
    void theLimitOfTheLevelApplies() {
        settings.levelSettings(1).maxAlerts(2);

        assertInstanceOf(Done.class, service.add(manager, "a", IRON).join());
        assertInstanceOf(Done.class, service.add(manager, "b", IRON).join());
        assertEquals(
                "company.alerts.error.limit",
                refusal(service.add(manager, "c", IRON).join()));
        assertEquals(2, alerts.lastLimit());
    }

    @Test
    void theGlobalLimitAppliesWithoutLevelLimit() {
        settings.alerts().maxPerCompany(4);

        service.add(manager, "a", IRON).join();

        assertEquals(4, alerts.lastLimit());
        var overview = (Overview)
                assertInstanceOf(Done.class, service.overview(manager).join()).value();
        assertEquals(4, overview.limit());
    }

    @Test
    void higherLevelsUseTheirOwnLimit() {
        settings.levelSettings(2).maxAlerts(6);
        companies.add(company(COMPANY, 2));

        service.add(manager, "a", IRON).join();

        assertEquals(6, alerts.lastLimit());
    }

    @Test
    void aLevelWithoutAlertsRefusesSaving() {
        settings.levelSettings(1).maxAlerts(0);

        assertEquals(
                "company.alerts.error.disabled",
                refusal(service.add(manager, "a", IRON).join()));
        settings.levelSettings(1).maxAlerts(null);
        settings.alerts().enabled(false);
        assertEquals(
                "company.alerts.error.disabled",
                refusal(service.add(manager, "a", IRON).join()));
    }

    @Test
    void namesAreUniqueAndChecked() {
        service.add(manager, "iron", IRON).join();

        assertEquals(
                "company.alerts.error.nameTaken",
                refusal(service.add(manager, "iron", IRON).join()));
        assertEquals(
                "company.alerts.error.name",
                refusal(service.add(manager, "two words", IRON).join()));
        assertEquals(
                "company.alerts.error.name",
                refusal(service.add(manager, "x".repeat(33), IRON).join()));
        assertEquals(
                "company.alerts.error.name",
                refusal(service.add(manager, "", IRON).join()));
    }

    @Test
    void alertsAreSwitchedAndRemoved() {
        alerts.put(COMPANY, "iron", IRON);

        assertInstanceOf(Done.class, service.enable(manager, "iron", false).join());
        assertTrue(alerts.retrieveEnabled().join().isEmpty());
        assertInstanceOf(Done.class, service.remove(manager, "iron").join());
        assertTrue(alerts.retrieveByCompany(COMPANY).join().isEmpty());
        assertEquals(
                "company.alerts.error.unknown",
                refusal(service.remove(manager, "iron").join()));
        assertEquals(
                "company.alerts.error.unknown",
                refusal(service.find(manager, "iron").join()));
    }

    @Test
    void alertsOfOtherCompaniesAreOutOfReach() {
        companies.add(company(8));
        alerts.put(8, "theirs", IRON);

        assertEquals(
                "company.alerts.error.unknown",
                refusal(service.remove(manager, "theirs").join()));
        assertEquals(1, alerts.retrieveByCompany(8).join().size());
    }
}
