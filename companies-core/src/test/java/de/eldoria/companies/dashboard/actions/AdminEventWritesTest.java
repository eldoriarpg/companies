/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.components.event.EventState;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard's admin tools for server events: allowed with the permission of {@code /companyadmin event}, new
 * events read by the command's form, changes refused where the command refuses them, and every change written to the
 * admin log.
 */
class AdminEventWritesTest {
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
        var result = perform(Actions.ADMIN_EVENTS_LIST, "{}", Permission.Admin.OFFERS);

        assertFalse(result.ok());
        assertEquals("companyadmin.error.permission", result.reason());
    }

    @Test
    void anEventIsCreatedFromTheFieldsOfTheForm() {
        var result = events(
                Actions.ADMIN_EVENTS_CREATE,
                "{\"name\":\"Harvest\",\"kind\":\"REWARD_MULTIPLIER\",\"value\":\"1.5\",\"materials\":\"wheat\","
                        + "\"start\":\"2h\",\"end\":\"1d\",\"funding\":\"MINTED\",\"budget\":\"500\"}");

        assertTrue(result.ok(), result.message());
        var id = result.data().path("id").asInt();
        var stored = world.serverEvents.event(id).orElseThrow();
        assertEquals("Harvest", stored.name());
        assertEquals(EventState.SCHEDULED, stored.state());
        assertEquals("Ada@dashboard", stored.createdBy());
        assertEquals("reward_multiplier", result.data().path("kind").asString());
        assertEquals("minted", result.data().path("funding").asString());
        assertEquals(500.0, result.data().path("budget").asDouble());
        assertTrue(world.adminActions.stream()
                .anyMatch(line -> line.contains("Ada@dashboard") && line.contains("event create")));
    }

    @Test
    void anInvalidFieldIsRefusedWithTheCommandsMessage() {
        var result = events(
                Actions.ADMIN_EVENTS_CREATE,
                "{\"name\":\"Tax\",\"kind\":\"TAX_REDUCTION\",\"value\":\"lots\",\"start\":\"now\",\"end\":\"1d\"}");

        assertFalse(result.ok());
        assertEquals("companyadmin.event.error.value", result.reason());
        assertTrue(world.serverEvents.live().isEmpty());
    }

    @Test
    void theListShowsTheNewestEventsWithTheirOffers() {
        var id = created("Tax", "TAX_REDUCTION", "5");

        var result = events(Actions.ADMIN_EVENTS_LIST, "{}");

        assertTrue(result.ok(), result.message());
        var first = result.data().path("events").get(0);
        assertEquals(id, first.path("id").asInt());
        assertEquals("scheduled", first.path("state").asString());
        assertEquals(0, first.path("offers").size());
    }

    @Test
    void endingAWaitingEventIsRefusedAndCancellingItIsLogged() {
        var id = created("Fee", "FEE_REDUCTION", "50");

        var end = events(Actions.ADMIN_EVENTS_END, "{\"event\":" + id + "}");
        var cancel = events(Actions.ADMIN_EVENTS_CANCEL, "{\"event\":" + id + "}");
        var again = events(Actions.ADMIN_EVENTS_CANCEL, "{\"event\":" + id + "}");

        assertFalse(end.ok());
        assertEquals("companyadmin.event.error.refused.end", end.reason());
        assertTrue(cancel.ok(), cancel.message());
        assertEquals("cancelled", cancel.data().path("state").asString());
        assertEquals("Ada@dashboard", cancel.data().path("closedBy").asString());
        assertFalse(again.ok());
        assertEquals("companyadmin.event.error.closed", again.reason());
        assertTrue(world.adminActions.stream().anyMatch(line -> line.contains("event cancel")));
    }

    @Test
    void anEventIsExtendedAndItsBudgetChanged() {
        var id = created("Boost", "REWARD_MULTIPLIER", "2");
        var before = world.serverEvents.event(id).orElseThrow().ends();

        var extend = events(Actions.ADMIN_EVENTS_EXTEND, "{\"event\":" + id + ",\"duration\":\"12h\"}");
        var budget = events(Actions.ADMIN_EVENTS_BUDGET, "{\"event\":" + id + ",\"budget\":250}");
        var wrong = events(Actions.ADMIN_EVENTS_EXTEND, "{\"event\":" + id + ",\"duration\":\"soon\"}");
        var negative = events(Actions.ADMIN_EVENTS_BUDGET, "{\"event\":" + id + ",\"budget\":-1}");

        assertTrue(extend.ok(), extend.message());
        assertEquals(
                before.plusHours(12), world.serverEvents.event(id).orElseThrow().ends());
        assertTrue(budget.ok(), budget.message());
        assertEquals(250.0, world.serverEvents.event(id).orElseThrow().budget());
        assertEquals("companyadmin.event.error.duration", wrong.reason());
        assertEquals("companyadmin.event.error.budget", negative.reason());
    }

    @Test
    void anUnknownEventIsNamed() {
        var result = events(Actions.ADMIN_EVENTS_END, "{\"event\":999}");

        assertFalse(result.ok());
        assertEquals("companyadmin.event.error.unknown", result.reason());
    }

    private int created(String name, String kind, String value) {
        var funded = kind.equals("REWARD_MULTIPLIER") ? ",\"funding\":\"MINTED\",\"budget\":\"100\"" : "";
        var result = events(
                Actions.ADMIN_EVENTS_CREATE,
                "{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\",\"value\":\"" + value
                        + "\",\"start\":\"2h\",\"end\":\"1d\"" + funded + "}");
        assertTrue(result.ok(), result.message());
        return result.data().path("id").asInt();
    }

    private ActionResult events(String action, String args) {
        return perform(action, args, Permission.Admin.EVENTS);
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
