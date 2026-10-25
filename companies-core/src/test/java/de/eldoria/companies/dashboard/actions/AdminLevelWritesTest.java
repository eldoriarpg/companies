/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.configuration.elements.NodeType;
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
 * The dashboard's level editor: allowed with the permission of {@code /companyadmin level} (recalculating with the one
 * of {@code calclevel}), every change shared with the other nodes and written to the admin log, and a form with one
 * invalid value changing nothing.
 */
class AdminLevelWritesTest {
    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID admin;
    private int starterLevels;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        admin = world.player("Ada");
        starterLevels = world.companySettings.level().size();
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

    private ActionResult level(String action, String args) {
        return perform(action, args, Permission.Admin.LEVEL);
    }

    @Test
    void editingNeedsTheLevelPermissionAndRecalculatingItsOwn() {
        assertEquals(
                "companyadmin.error.permission",
                perform(Actions.ADMIN_LEVELS_CREATE, "{}", Permission.Admin.CALC_LEVEL)
                        .reason());
        assertEquals(
                "companyadmin.error.permission",
                perform(Actions.ADMIN_LEVELS_RECALCULATE, "{}", Permission.Admin.LEVEL)
                        .reason());
        assertEquals(starterLevels, world.companySettings.level().size());

        assertTrue(perform(Actions.ADMIN_LEVELS_RECALCULATE, "{}", Permission.Admin.CALC_LEVEL)
                .ok());
        assertEquals(1, world.recalculations.get());
        assertTrue(world.adminActions.stream().anyMatch(line -> line.contains("calclevel")));
    }

    @Test
    void theLevelsAreListedForThePrimary() {
        var listed = level(Actions.ADMIN_LEVELS_LIST, "{}");

        assertTrue(listed.ok(), listed.reason());
        assertTrue(listed.data().path("editable").asBoolean());
        assertEquals(starterLevels, listed.data().path("levels").size());
        assertEquals(1, listed.data().path("levels").get(0).path("level").asInt());
    }

    @Test
    void aLevelIsAddedSavedMovedAndRemovedAndEachChangeIsShared() {
        var created = level(Actions.ADMIN_LEVELS_CREATE, "{}");
        assertTrue(created.ok(), created.reason());
        var number = created.data().path("level").asInt();
        assertEquals(starterLevels + 1, number);

        var saved = level(
                Actions.ADMIN_LEVELS_EDIT,
                "{\"level\":%d,\"values\":{\"name\":\"Guild\",\"order_count\":\"12\",\"tax_percent\":\"2.5\"},\"position\":1}"
                        .formatted(number));
        assertTrue(saved.ok(), saved.reason());
        assertEquals(1, saved.data().path("level").asInt());
        assertEquals("Guild", saved.data().path("name").asString());
        assertEquals(12, saved.data().path("orderCount").asInt());
        assertEquals(2.5, saved.data().path("taxPercent").asDouble());

        var cleared = level(Actions.ADMIN_LEVELS_EDIT, "{\"level\":1,\"values\":{\"tax_percent\":null}}");
        assertTrue(cleared.ok(), cleared.reason());
        assertFalse(cleared.data().path("taxPercent").isNumber());

        var moved = level(Actions.ADMIN_LEVELS_MOVE, "{\"level\":1,\"to\":2}");
        assertTrue(moved.ok(), moved.reason());
        assertEquals("Guild", moved.data().path("levels").get(1).path("name").asString());

        var removed = level(Actions.ADMIN_LEVELS_REMOVE, "{\"level\":2}");
        assertTrue(removed.ok(), removed.reason());
        assertEquals(starterLevels, removed.data().path("levels").size());

        assertEquals(5, world.levelShares.get());
        assertTrue(world.adminActions.stream().anyMatch(line -> line.contains("Ada@dashboard")));
    }

    @Test
    void aSecondaryShowsTheLevelsButRefusesChanges() {
        world.nodeType = NodeType.SECONDARY;

        assertFalse(
                level(Actions.ADMIN_LEVELS_LIST, "{}").data().path("editable").asBoolean());
        assertEquals(
                "error.secondarynode", level(Actions.ADMIN_LEVELS_CREATE, "{}").reason());
        assertEquals(starterLevels, world.companySettings.level().size());
        assertEquals(0, world.levelShares.get());
    }

    @Test
    void theLastLevelStaysAndChangesThatChangeNothingAreNotShared() {
        assertTrue(
                level(Actions.ADMIN_LEVELS_EDIT, "{\"level\":1,\"values\":{}}").ok());
        assertTrue(level(Actions.ADMIN_LEVELS_MOVE, "{\"level\":1,\"to\":1}").ok());
        assertEquals(
                "dashboard.invalid",
                level(Actions.ADMIN_LEVELS_EDIT, "{\"level\":1,\"values\":{\"name\":\" \"}}")
                        .reason());
        while (world.companySettings.level().size() > 1) {
            assertTrue(level(Actions.ADMIN_LEVELS_REMOVE, "{\"level\":1}").ok());
        }
        var shared = world.levelShares.get();

        assertEquals(
                "companyadmin.level.lastLevel",
                level(Actions.ADMIN_LEVELS_REMOVE, "{\"level\":1}").reason());
        assertEquals(1, world.companySettings.level().size());
        assertEquals(starterLevels - 1, shared);
    }

    @Test
    void aFormWithOneInvalidValueChangesNothing() {
        var before = world.companySettings.level(1).orElseThrow().levelName();

        var refused = level(
                Actions.ADMIN_LEVELS_EDIT, "{\"level\":1,\"values\":{\"name\":\"Guild\",\"order_count\":\"many\"}}");

        assertFalse(refused.ok());
        assertEquals("error.invalidNumber", refused.reason());
        assertEquals("text of level.orderCount: text of error.invalidNumber", refused.message());
        assertEquals(before, world.companySettings.level(1).orElseThrow().levelName());
        assertEquals(0, world.levelShares.get());
        assertEquals(
                "error.unkownField",
                level(Actions.ADMIN_LEVELS_EDIT, "{\"level\":1,\"values\":{\"colour\":\"red\"}}")
                        .reason());
        assertEquals(
                "error.invalidLevel",
                level(Actions.ADMIN_LEVELS_REMOVE, "{\"level\":99}").reason());
    }
}
