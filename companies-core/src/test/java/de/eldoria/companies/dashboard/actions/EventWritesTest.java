/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.PrizeState;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Events page of players: the offers running or starting within a day, a goal with its contributors where players
 * working alone stay unnamed, a contest with its ranking, prizes and the part of the player's company, and joining or
 * leaving a contest only for members who manage the company's orders.
 */
class EventWritesTest {
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-00000000000e");

    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID manager;
    private UUID worker;
    private UUID loner;
    private int company;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        manager = world.player("Mira");
        worker = world.player("Wren");
        loner = world.player("Lone");
        company = world.company("Miners", Map.of(manager, List.of(CompanyPermission.MANAGE_ORDERS), worker, List.of()));
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    @Test
    void theOffersRunningOrStartingWithinADayAreListed() {
        var goal = world.goal("Logs for the bridge", 0, true);
        var contest = world.contest("Log race", 0, true);
        var soon = world.goal("Tomorrow", 2, false);
        world.goal("Next week", 72, false);
        var cancelled = world.goal("Called off", 0, true);
        world.offers.state(cancelled, OfferState.ACTIVE, OfferState.CANCELLED, Times.now());
        world.offers.join(contest, company, Times.now());

        var result = perform(manager, Actions.EVENTS_LIST, "{}");

        assertTrue(result.ok());
        var offers = list(result.data().path("offers"));
        assertEquals(
                List.of(goal, contest, soon),
                offers.stream().map(offer -> offer.path("id").asInt()).sorted().toList());
        var race = offers.stream()
                .filter(offer -> offer.path("id").asInt() == contest)
                .findFirst()
                .orElseThrow();
        assertEquals("contest", race.path("kind").asString());
        assertEquals("active", race.path("state").asString());
        assertTrue(race.path("joined").asBoolean());
        assertEquals("oak_log", race.path("item").path("icon").asString());
        assertEquals(70.0, race.path("prizeTotal").asDouble());
        assertTrue(result.data().path("member").asBoolean());
        assertTrue(result.data().path("canManage").asBoolean());
    }

    @Test
    void aGoalNamesTheCompaniesThatDeliveredButNoPlayerWorkingAlone() {
        var goal = world.goal("Logs for the bridge", 0, true);
        world.deliveredChunk(goal, 9001, company, null, 30);
        world.deliveredChunk(goal, 9002, 0, loner, 10);

        var result = perform(worker, Actions.EVENTS_INFO, "{\"offer\":" + goal + "}");

        assertTrue(result.ok());
        var contributors = list(result.data().path("contributors"));
        assertEquals(2, contributors.size());
        assertEquals("Miners", contributors.get(0).path("name").asString());
        assertEquals(30, contributors.get(0).path("delivered").asInt());
        assertFalse(contributors.get(1).hasNonNull("name"));
        assertTrue(contributors.get(1).path("alone").asBoolean());
        assertFalse(result.data().toString().contains("Lone"));
        assertEquals(10, result.data().path("chunkSize").asInt());
        assertFalse(result.data().path("canManage").asBoolean());
    }

    @Test
    void aContestShowsItsRankingPrizesAndThePartOfTheCompany() {
        var contest = world.contest("Log race", 0, true);
        var rivals = world.company("Rivals", Map.of(world.player("Rex"), List.of(CompanyPermission.OWNER)));
        world.offers.join(contest, company, Times.now());
        world.offers.join(contest, rivals, Times.now());
        world.offers.deliver(contest, rivals, loner, 20, Times.now());
        world.offers.deliver(contest, company, worker, 5, Times.now());

        var result = perform(worker, Actions.EVENTS_INFO, "{\"offer\":" + contest + "}");

        assertTrue(result.ok());
        var ranking = list(result.data().path("ranking"));
        assertEquals(
                List.of("Rivals", "Miners"),
                ranking.stream().map(entry -> entry.path("name").asString()).toList());
        assertTrue(ranking.get(1).path("own").asBoolean());
        var own = result.data().path("own");
        assertEquals("Miners", own.path("company").asString());
        assertEquals(2, own.path("place").asInt());
        assertEquals(5, own.path("delivered").asInt());
        assertTrue(own.path("joined").asBoolean());
        var prizes = list(result.data().path("prizes"));
        assertEquals(
                List.of(50.0, 20.0),
                prizes.stream().map(prize -> prize.path("amount").asDouble()).toList());
        assertEquals(2, result.data().path("offer").path("place").asInt());
    }

    @Test
    void membersWhoManageOrdersJoinAndLeaveContests() {
        var contest = world.contest("Log race", 0, true);

        var joined = perform(manager, Actions.EVENTS_JOIN, "{\"offer\":" + contest + "}");
        var again = perform(manager, Actions.EVENTS_JOIN, "{\"offer\":" + contest + "}");
        var left = perform(manager, Actions.EVENTS_LEAVE, "{\"offer\":" + contest + "}");

        assertTrue(joined.ok());
        assertTrue(joined.data().path("own").path("joined").asBoolean());
        assertFalse(again.ok());
        assertEquals("company.contest.error.already", again.reason());
        assertTrue(left.ok());
        assertFalse(left.data().path("own").path("joined").asBoolean());
    }

    @Test
    void othersCannotJoin() {
        var contest = world.contest("Log race", 0, true);
        var later = world.contest("Later race", 3, false);

        var member = perform(worker, Actions.EVENTS_JOIN, "{\"offer\":" + contest + "}");
        var alone = perform(loner, Actions.EVENTS_JOIN, "{\"offer\":" + contest + "}");
        var early = perform(manager, Actions.EVENTS_JOIN, "{\"offer\":" + later + "}");

        assertEquals("company.contest.error.permission", member.reason());
        assertEquals("error.noMember", alone.reason());
        assertEquals("company.contest.error.notStarted", early.reason());
        assertTrue(world.offers.entry(contest, company).isEmpty());
    }

    @Test
    void anOfferNotShownIsUnknown() {
        var later = world.goal("Next week", 72, false);

        var result = perform(manager, Actions.EVENTS_INFO, "{\"offer\":" + later + "}");

        assertFalse(result.ok());
        assertEquals("dashboard.offerUnknown", result.reason());
    }

    @Test
    void aClosedContestKeepsItsFinalRankingAndPrizeWinners() {
        var contest = world.contest("Log race", 0, true);
        world.offers.join(contest, company, Times.now());
        world.offers.deliver(contest, company, worker, 5, Times.now());
        world.offers.state(contest, OfferState.ACTIVE, OfferState.ENDED, Times.now());
        world.offers.settle(
                contest,
                List.of(
                        new ContestPrize(1, 50, company, "Miners", PrizeState.PAID),
                        new ContestPrize(2, 20, null, null, PrizeState.NONE)),
                Times.now());

        var listed = perform(manager, Actions.EVENTS_LIST, "{}");
        var result = perform(manager, Actions.EVENTS_INFO, "{\"offer\":" + contest + "}");

        assertEquals(0, listed.data().path("offers").size());
        assertTrue(result.ok(), result.message());
        assertEquals("ended", result.data().path("offer").path("state").asString());
        assertEquals("Miners", result.data().path("ranking").get(0).path("name").asString());
        var prizes = list(result.data().path("prizes"));
        assertEquals("Miners", prizes.get(0).path("winner").asString());
        assertEquals("paid", prizes.get(0).path("state").asString());
        assertEquals("none", prizes.get(1).path("state").asString());
    }

    @Test
    void aGoalThatEndedStillOpensForItsLateChunks() {
        var goal = world.goal("Logs for the bridge", 0, true);
        world.offers.state(goal, OfferState.ACTIVE, OfferState.ENDED, Times.now());

        var result = perform(worker, Actions.EVENTS_INFO, "{\"offer\":" + goal + "}");

        assertTrue(result.ok(), result.message());
        assertEquals("ended", result.data().path("offer").path("state").asString());
    }

    @Test
    void anOfferCancelledBeforeItStartedStaysUnknown() {
        var contest = world.contest("Called off", 2, false);
        world.offers.state(contest, OfferState.SCHEDULED, OfferState.CANCELLED, Times.now());

        var result = perform(manager, Actions.EVENTS_INFO, "{\"offer\":" + contest + "}");

        assertEquals("dashboard.offerUnknown", result.reason());
    }

    @Test
    void theListShowsTheServerEventsRunningOrStartingWithinADay() {
        var now = Times.now();
        var running = world.eventService
                .create(ServerEvent.draft(
                        "Tax week",
                        EventKind.TAX_REDUCTION,
                        5,
                        List.of(),
                        List.of(),
                        now.plusSeconds(2),
                        now.plusDays(2),
                        null,
                        null,
                        null,
                        null,
                        "Ada",
                        now))
                .id();
        world.eventService.create(ServerEvent.draft(
                "Far away",
                EventKind.FEE_REDUCTION,
                50,
                List.of(),
                List.of(),
                now.plusDays(3),
                now.plusDays(4),
                null,
                null,
                null,
                null,
                "Ada",
                now));

        var result = perform(world.player("Bea"), Actions.EVENTS_LIST, "{}");

        assertTrue(result.ok(), result.message());
        var events = list(result.data().path("serverEvents"));
        assertEquals(
                List.of(running),
                events.stream().map(event -> event.path("id").asInt()).toList());
        assertEquals("tax_reduction", events.getFirst().path("kind").asString());
        assertTrue(events.getFirst().path("budgetLeft").isMissingNode());
    }

    private ActionResult perform(UUID player, String action, String args) {
        var request = new ActionRequest(
                "action-1",
                Protocol.VERSION,
                "session",
                player,
                action,
                Protocol.mapper().readTree(args));
        var now = Times.now();
        var session = new StoredSession("session", player, "Mira", NODE, now, now, now, now, null, Set.of());
        return world.writes.perform(request, session);
    }

    private static List<JsonNode> list(JsonNode array) {
        var nodes = new ArrayList<JsonNode>();
        array.forEach(nodes::add);
        return nodes;
    }
}
