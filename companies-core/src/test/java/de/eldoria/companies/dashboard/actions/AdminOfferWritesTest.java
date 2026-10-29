/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.PrizeState;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard's admin tools for event offers: allowed with the permission of {@code /companyadmin offers}, new offers
 * checked as the command checks them, state changes refused where the command refuses them, a stuck prize settled,
 * and every change and look written to the admin log.
 */
class AdminOfferWritesTest {
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-00000000000f");

    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID admin;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        admin = world.player("Ada");
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    @Test
    void withoutThePermissionNothingIsShown() {
        var result = perform(Actions.ADMIN_OFFERS_LIST, "{}", Permission.Admin.ORDERS);

        assertFalse(result.ok());
        assertEquals("companyadmin.error.permission", result.reason());
    }

    @Test
    void aGoalIsCreatedAsTheCommandCreatesIt() {
        var result = offers(
                Actions.ADMIN_OFFERS_CREATE,
                "{\"kind\":\"goal\",\"name\":\"Bridge logs\",\"money\":\"minted\",\"material\":\"oak_log\","
                        + "\"amount\":500,\"price\":2,\"chunk\":50,\"open\":5,\"start\":0,\"hours\":48}");

        assertTrue(result.ok(), result.message());
        var id = result.data().path("id").asInt();
        var stored = world.offers.offer(id).orElseThrow();
        assertEquals(OfferKind.GOAL, stored.kind());
        assertEquals("OAK_LOG", stored.line().material());
        assertEquals(50, stored.chunkSize());
        assertEquals(5, stored.openChunks());
        assertEquals("Ada", stored.createdBy());
        assertEquals("minted", result.data().path("source").asString());
        assertEquals(1, world.offerChecks.get());
        assertTrue(world.adminActions.stream()
                .anyMatch(line -> line.contains("Ada@dashboard") && line.contains("offer goal create")));
    }

    @Test
    void aContestTakesItsPrizesAsNumbers() {
        var result = offers(
                Actions.ADMIN_OFFERS_CREATE,
                "{\"kind\":\"contest\",\"name\":\"Log race\",\"money\":\"bank\",\"criteria\":\"#logs\","
                        + "\"amount\":100,\"price\":1.5,\"prizes\":[500,250.5],\"start\":2,\"hours\":24}");

        assertTrue(result.ok(), result.message());
        var stored = world.offers.offer(result.data().path("id").asInt()).orElseThrow();
        assertEquals(List.of(500.0, 250.5), stored.prizes());
        assertEquals("#logs", stored.line().criteria());
        assertEquals("bank", stored.account());
        assertEquals(150 + 750.5, stored.budget(), 0.001);
        assertEquals("criteria", result.data().path("line").asString());
    }

    @Test
    void whatTheCommandRefusesIsRefused() {
        assertEquals(
                "companyadmin.offers.error.material",
                offers(Actions.ADMIN_OFFERS_CREATE, goal("\"material\":\"not_an_item\"", 2))
                        .reason());
        assertEquals(
                "companyadmin.offers.error.price",
                offers(Actions.ADMIN_OFFERS_CREATE, goal("\"material\":\"oak_log\"", 0))
                        .reason());
        assertEquals(
                "companyadmin.offers.error.prizes",
                offers(
                                Actions.ADMIN_OFFERS_CREATE,
                                "{\"kind\":\"contest\",\"name\":\"Race\",\"money\":\"minted\",\"material\":\"oak_log\","
                                        + "\"amount\":10,\"price\":1,\"prizes\":[-5],\"start\":0,\"hours\":1}")
                        .reason());
        assertEquals(
                "dashboard.invalid",
                offers(Actions.ADMIN_OFFERS_CREATE, "{\"kind\":\"raid\"}").reason());
        assertEquals(
                "companyadmin.offers.error.openChunks",
                offers(Actions.ADMIN_OFFERS_CREATE, goal("\"material\":\"oak_log\",\"open\":0", 2))
                        .reason());
        assertEquals(
                "companyadmin.offers.error.openChunks",
                offers(Actions.ADMIN_OFFERS_CREATE, goal("\"material\":\"oak_log\",\"open\":101", 2))
                        .reason());
        assertTrue(world.offers.offers(10).isEmpty());
    }

    @Test
    void theListShowsStateProgressAndMoney() {
        var goal = world.goal("Bridge logs", 0, true);

        var result = offers(Actions.ADMIN_OFFERS_LIST, "{}");

        assertTrue(result.ok());
        var row = result.data().path("offers").get(0);
        assertEquals(goal, row.path("id").asInt());
        assertEquals("active", row.path("state").asString());
        assertEquals(200.0, row.path("budget").asDouble());
        assertEquals(200.0, row.path("left").asDouble());
        assertEquals("minted", row.path("source").asString());
    }

    @Test
    void theInfoNamesPlayersWorkingAloneAndIsLogged() {
        var goal = world.goal("Bridge logs", 0, true);
        var loner = world.player("Lone");
        var company = world.company("Miners", Map.of(world.player("Mira"), List.of(CompanyPermission.OWNER)));
        world.deliveredChunk(goal, 9001, company, null, 30);
        world.deliveredChunk(goal, 9002, 0, loner, 10);

        var result = offers(Actions.ADMIN_OFFERS_INFO, "{\"offer\":" + goal + "}");

        assertTrue(result.ok());
        var contributors = result.data().path("contributors");
        assertEquals("Miners", contributors.get(0).path("name").asString());
        assertEquals("Lone", contributors.get(1).path("name").asString());
        assertTrue(contributors.get(1).path("alone").asBoolean());
        assertEquals("Ada", result.data().path("createdBy").asString());
        assertTrue(world.adminActions.stream().anyMatch(line -> line.contains("offer info")));
    }

    @Test
    void offersArePausedResumedAndCancelledAsTheCommandDoes() {
        var goal = world.goal("Bridge logs", 0, true);
        var args = "{\"offer\":" + goal + "}";

        var paused = offers(Actions.ADMIN_OFFERS_PAUSE, args);
        var twice = offers(Actions.ADMIN_OFFERS_PAUSE, args);
        var resumed = offers(Actions.ADMIN_OFFERS_RESUME, args);
        var cancelled = offers(Actions.ADMIN_OFFERS_CANCEL, args);
        var again = offers(Actions.ADMIN_OFFERS_CANCEL, args);

        assertEquals("paused", paused.data().path("state").asString());
        assertEquals("companyadmin.offers.error.pause", twice.reason());
        assertTrue(resumed.ok());
        assertEquals("cancelled", cancelled.data().path("state").asString());
        assertEquals("companyadmin.offers.error.cancel", again.reason());
        assertEquals(
                OfferState.CANCELLED, world.offers.offer(goal).orElseThrow().state());
        assertEquals(3, world.offerChecks.get());
        assertEquals(
                "companyadmin.offers.error.unknown",
                offers(Actions.ADMIN_OFFERS_PAUSE, "{\"offer\":999}").reason());
    }

    @Test
    void onlyGoalsChangeTheirOpenChunks() {
        var goal = world.goal("Bridge logs", 0, true);
        var contest = world.contest("Log race", 0, true);

        var opened = offers(Actions.ADMIN_OFFERS_OPEN, "{\"offer\":" + goal + ",\"count\":7}");
        var notGoal = offers(Actions.ADMIN_OFFERS_OPEN, "{\"offer\":" + contest + ",\"count\":7}");
        var tooMany = offers(Actions.ADMIN_OFFERS_OPEN, "{\"offer\":" + goal + ",\"count\":101}");

        assertEquals(7, opened.data().path("openChunks").asInt());
        assertEquals("companyadmin.offers.error.notGoal", notGoal.reason());
        assertEquals("companyadmin.offers.error.numbers", tooMany.reason());
        assertTrue(world.adminActions.stream().anyMatch(line -> line.contains("offer open")));
    }

    @Test
    void aPrizeStuckWhileBeingPaidIsSettled() {
        var contest = world.contest("Log race", 0, true);
        var company = world.company("Miners", Map.of(world.player("Mira"), List.of(CompanyPermission.OWNER)));
        world.offers.state(contest, OfferState.ACTIVE, OfferState.ENDED, Times.now());
        world.offers.settle(
                contest, List.of(new ContestPrize(1, 50, company, "Miners", PrizeState.PENDING)), Times.now());
        var notStuck =
                offers(Actions.ADMIN_OFFERS_PRIZE, "{\"offer\":" + contest + ",\"place\":1,\"outcome\":\"paid\"}");
        world.offers.claimPrize(contest, 1);

        var settled =
                offers(Actions.ADMIN_OFFERS_PRIZE, "{\"offer\":" + contest + ",\"place\":1,\"outcome\":\"paid\"}");

        assertEquals("companyadmin.offers.error.notPaying", notStuck.reason());
        assertTrue(settled.ok(), settled.message());
        assertEquals("paid", settled.data().path("prizes").get(0).path("state").asString());
        assertEquals(
                "dashboard.invalid",
                offers(Actions.ADMIN_OFFERS_PRIZE, "{\"offer\":" + contest + ",\"place\":1,\"outcome\":\"maybe\"}")
                        .reason());
    }

    private static String goal(String item, double price) {
        return "{\"kind\":\"goal\",\"name\":\"Logs\",\"money\":\"minted\"," + item + ",\"amount\":100,\"price\":"
                + price + ",\"chunk\":10,\"start\":0,\"hours\":24}";
    }

    private ActionResult offers(String action, String args) {
        return perform(action, args, Permission.Admin.OFFERS);
    }

    private ActionResult perform(String action, String args, String... permissions) {
        var request = new ActionRequest(
                "action-1",
                Protocol.VERSION,
                "session",
                admin,
                action,
                Protocol.mapper().readTree(args));
        var now = Times.now();
        var session = new StoredSession("session", admin, "Ada", NODE, now, now, now, now, null, Set.of(permissions));
        return world.writes.perform(request, session);
    }
}
