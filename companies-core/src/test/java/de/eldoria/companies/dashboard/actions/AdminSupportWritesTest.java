/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard's admin tools for players and companies, each allowed with the permission of its command only and
 * written to the admin log.
 */
class AdminSupportWritesTest {
    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID admin;
    private UUID owner;
    private UUID worker;
    private int company;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        admin = world.player("Ada");
        owner = world.player("Orla");
        worker = world.player("Moss");
        company = world.company("Miners", Map.of(owner, List.of(CompanyPermission.OWNER), worker, List.of()));
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    private ActionResult perform(String action, String args, String... permissions) {
        var session = new StoredSession(
                "session",
                admin,
                "Ada",
                UUID.randomUUID(),
                Times.now(),
                Times.now(),
                Times.now(),
                Times.now(),
                null,
                Set.of(permissions));
        return world.writes.perform(
                new ActionRequest(
                        "act",
                        Protocol.VERSION,
                        session.id(),
                        admin,
                        action,
                        Protocol.mapper().readTree(args)),
                session);
    }

    @Test
    void eachToolNeedsThePermissionOfItsCommand() {
        assertEquals(
                "companyadmin.error.permission",
                perform(
                                Actions.ADMIN_PLAYERS_GRANT_MONEY,
                                "{\"player\":\"Orla\",\"amount\":5}",
                                Permission.Admin.PLAYER)
                        .reason());
        assertEquals(
                "companyadmin.error.permission",
                perform(Actions.ADMIN_COMPANIES_DELETE, "{\"company\":\"Miners\"}", Permission.Admin.RENAME)
                        .reason());
        assertTrue(world.adminActions.isEmpty());
    }

    @Test
    void aPlayerIsLookedUpWithCompanyAndRole() {
        var found = perform(Actions.ADMIN_PLAYERS_LOOKUP, "{\"player\":\"orla\"}", Permission.Admin.PLAYER);

        assertTrue(found.ok(), found.reason());
        assertEquals("Orla", found.data().path("player").path("name").asString());
        assertEquals("Miners", found.data().path("company").asString());
        assertTrue(found.data().path("owner").asBoolean());
        assertEquals(
                "error.unknownPlayer",
                perform(Actions.ADMIN_PLAYERS_LOOKUP, "{\"player\":\"nobody\"}", Permission.Admin.PLAYER)
                        .reason());
        assertTrue(world.adminActions.getFirst().contains("Ada@dashboard player player=Orla"));
    }

    @Test
    void grantedMoneyIsDepositedAndGrantedItemsWaitToBeCollected() {
        var money = perform(
                Actions.ADMIN_PLAYERS_GRANT_MONEY, "{\"player\":\"Moss\",\"amount\":250}", Permission.Admin.GRANT);
        var items = perform(
                Actions.ADMIN_PLAYERS_GRANT_ITEMS,
                "{\"player\":\"Moss\",\"material\":\"minecraft:oak_log\",\"amount\":32}",
                Permission.Admin.GRANT);

        assertTrue(money.ok(), money.reason());
        assertEquals(250.0, world.wallets.get(worker));
        assertTrue(items.ok(), items.reason());
        assertEquals(
                32,
                world.orders.retrieveItemReturnCount(worker, ReturnKind.OWNER).join());
        assertEquals(
                "dashboard.invalid",
                perform(Actions.ADMIN_PLAYERS_GRANT_MONEY, "{\"player\":\"Moss\",\"amount\":0}", Permission.Admin.GRANT)
                        .reason());
        assertEquals(
                "dashboard.item.unknownMaterial",
                perform(
                                Actions.ADMIN_PLAYERS_GRANT_ITEMS,
                                "{\"player\":\"Moss\",\"material\":\"nothing\",\"amount\":1}",
                                Permission.Admin.GRANT)
                        .reason());
    }

    @Test
    void theTreasuryGetsAndLosesMoneyButNotBelowZero() {
        var given = perform(
                Actions.ADMIN_COMPANIES_TREASURY_GIVE,
                "{\"company\":\"Miners\",\"amount\":100}",
                Permission.Admin.TREASURY);

        assertTrue(given.ok(), given.reason());
        assertEquals(100.0, given.data().path("balance").asDouble());
        assertEquals(
                "company.bank.error.insufficient",
                perform(
                                Actions.ADMIN_COMPANIES_TREASURY_TAKE,
                                "{\"company\":\"Miners\",\"amount\":500}",
                                Permission.Admin.TREASURY)
                        .reason());
        var shown = perform(Actions.ADMIN_COMPANIES_TREASURY, "{\"company\":\"miners\"}", Permission.Admin.TREASURY);
        assertTrue(shown.ok(), shown.reason());
        assertEquals(100.0, shown.data().path("balance").asDouble());
        assertEquals(1, shown.data().path("entries").size());
    }

    @Test
    void aCompanyIsRenamedHandedOverAndDeleted() {
        world.company("Diggers", Map.of());
        assertEquals(
                "error.companyNameUsed",
                perform(
                                Actions.ADMIN_COMPANIES_RENAME,
                                "{\"company\":\"Miners\",\"name\":\"Diggers\"}",
                                Permission.Admin.RENAME)
                        .reason());
        assertTrue(perform(
                        Actions.ADMIN_COMPANIES_RENAME,
                        "{\"company\":\"Miners\",\"name\":\"Quarry\"}",
                        Permission.Admin.RENAME)
                .ok());

        var transferred = perform(
                Actions.ADMIN_COMPANIES_TRANSFER_OWNER,
                "{\"company\":\"Quarry\",\"player\":\"Moss\"}",
                Permission.Admin.TRANSFER_OWNER);
        assertTrue(transferred.ok(), transferred.reason());
        assertEquals(
                "error.noCompanyMember",
                perform(
                                Actions.ADMIN_COMPANIES_TRANSFER_OWNER,
                                "{\"company\":\"Quarry\",\"player\":\"Ada\"}",
                                Permission.Admin.TRANSFER_OWNER)
                        .reason());

        var deleted = perform(Actions.ADMIN_COMPANIES_DELETE, "{\"company\":\"Quarry\"}", Permission.Admin.DELETE);
        assertTrue(deleted.ok(), deleted.reason());
        assertTrue(
                world.companies.retrieveCompanyById(company).asFuture().join().isEmpty());
        assertEquals(
                "error.unknownCompany",
                perform(Actions.ADMIN_COMPANIES_DELETE, "{\"company\":\"Quarry\"}", Permission.Admin.DELETE)
                        .reason());
    }
}
