/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.job.JobState;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Jobs page: posting a contract, the lists of open, own and company jobs, the life of a contract from taking to
 * approval, disputes settled by admins, and who may see and do what.
 */
class JobWritesTest {
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-00000000000f");

    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID owner;
    private UUID manager;
    private UUID worker;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        owner = world.player("Olga");
        manager = world.player("Mira");
        worker = world.player("Wren");
        world.company("Builders", Map.of(manager, List.of(CompanyPermission.MANAGE_ORDERS), worker, List.of()));
        world.wallets.put(owner, 1000.0);
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    @Test
    void aContractIsPostedWithItsDescriptionAndTheMoneyHeld() {
        var result = perform(owner, Actions.JOBS_CONTRACT, contract(), Set.of(Permission.Orders.JOBS));

        assertTrue(result.ok(), result.message());
        var job = result.data().path("job");
        assertEquals("contract", job.path("kind").asString());
        assertEquals("open", job.path("state").asString());
        assertTrue(job.path("mine").asBoolean());
        assertEquals("Paint the barn red", result.data().path("description").asString());
        assertEquals(900.0, world.wallets.get(owner), 0.001);
    }

    @Test
    void postingNeedsThePermissionAndARule() {
        var refused = perform(owner, Actions.JOBS_CONTRACT, contract(), Set.of());
        var tooLong = perform(
                owner,
                Actions.JOBS_CONTRACT,
                "{\"title\":\"Barn\",\"price\":100,\"hours\":24000}",
                Set.of(Permission.Orders.JOBS));

        assertFalse(refused.ok());
        assertEquals("error.permission.postJob", refused.reason());
        assertFalse(tooLong.ok());
        assertEquals("order.job.error.due", tooLong.reason());
        assertEquals(1000.0, world.wallets.get(owner), 0.001);
    }

    @Test
    void aContractGoesFromTakingToApprovalAndTheSidesStayUnnamed() {
        var id = posted();

        var open = perform(manager, Actions.JOBS_LIST, "{}", Set.of());
        assertEquals(id, open.data().path("open").get(0).path("id").asInt());
        assertTrue(open.data().path("canManage").asBoolean());
        assertFalse(perform(worker, Actions.JOBS_TAKE, job(id), Set.of()).ok());
        var taken = perform(manager, Actions.JOBS_TAKE, job(id), Set.of());
        assertTrue(taken.ok(), taken.message());
        assertTrue(taken.data().path("job").path("ours").asBoolean());
        var done = perform(manager, Actions.JOBS_DONE, job(id), Set.of());
        assertEquals("done", done.data().path("job").path("state").asString());
        assertFalse(done.data().path("approveAt").isNull());
        var mine = perform(owner, Actions.JOBS_LIST, "{}", Set.of());
        assertEquals("done", mine.data().path("mine").get(0).path("state").asString());
        assertFalse(mine.data().toString().contains("Builders"));

        var approved = perform(owner, Actions.JOBS_APPROVE, job(id), Set.of());

        assertTrue(approved.ok(), approved.message());
        assertEquals("paid", approved.data().path("job").path("state").asString());
        assertEquals(100.0, world.wallets.getOrDefault(manager, 0.0), 0.001);
    }

    @Test
    void theCompanyAsksForMoreTimeAndTheOwnerGrantsOrExtends() {
        var id = posted();
        perform(manager, Actions.JOBS_TAKE, job(id), Set.of());

        var asked = perform(
                manager, Actions.JOBS_EXTENSION_ASK, "{\"job\":" + id + ",\"hours\":6,\"reason\":\"Big\"}", Set.of());
        assertTrue(asked.ok(), asked.message());
        assertEquals(6, asked.data().path("request").path("hours").asInt());
        var seen = perform(owner, Actions.JOBS_INFO, job(id), Set.of());
        assertEquals("Big", seen.data().path("request").path("reason").asString());
        var granted = perform(owner, Actions.JOBS_EXTENSION_GRANT, job(id), Set.of());
        assertTrue(granted.ok(), granted.message());
        assertEquals(6, granted.data().path("extendedHours").asInt());
        assertFalse(granted.data().path("request").has("hours"));
        var extended = perform(owner, Actions.JOBS_EXTEND, "{\"job\":" + id + ",\"hours\":4}", Set.of());
        assertEquals(10, extended.data().path("extendedHours").asInt());
        var tooMuch = perform(owner, Actions.JOBS_EXTEND, "{\"job\":" + id + ",\"hours\":10000}", Set.of());
        assertFalse(tooMuch.ok());
        assertEquals("order.job.error.extensionHours", tooMuch.reason());
        assertFalse(perform(manager, Actions.JOBS_EXTEND, "{\"job\":" + id + ",\"hours\":4}", Set.of())
                .ok());
    }

    @Test
    void aJobOfACompanyMemberCanNotBeTakenByItsCompanyAndPostingFollowsTheSettings() {
        world.wallets.put(worker, 500.0);
        var own = perform(worker, Actions.JOBS_CONTRACT, contract(), Set.of(Permission.Orders.JOBS))
                .data()
                .path("job")
                .path("id")
                .asInt();

        assertFalse(perform(manager, Actions.JOBS_INFO, job(own), Set.of())
                .data()
                .path("takeable")
                .asBoolean());
        assertTrue(perform(manager, Actions.JOBS_INFO, job(posted()), Set.of())
                .data()
                .path("takeable")
                .asBoolean());
        world.jobSettings.enabled(false);
        assertFalse(perform(owner, Actions.JOBS_LIST, "{}", Set.of(Permission.Orders.JOBS))
                .data()
                .path("canPost")
                .asBoolean());
    }

    @Test
    void strangersSeeOnlyOpenJobs() {
        var id = posted();
        perform(manager, Actions.JOBS_TAKE, job(id), Set.of());
        var stranger = world.player("Sten");

        var info = perform(stranger, Actions.JOBS_INFO, job(id), Set.of());

        assertFalse(info.ok());
        assertTrue(perform(worker, Actions.JOBS_INFO, job(id), Set.of()).ok());
    }

    @Test
    void anAdminSettlesADisputeFromTheDashboard() {
        var id = posted();
        perform(manager, Actions.JOBS_TAKE, job(id), Set.of());
        var disputed =
                perform(owner, Actions.JOBS_DISPUTE, "{\"job\":" + id + ",\"reason\":\"Nothing done\"}", Set.of());
        assertEquals("disputed", disputed.data().path("job").path("state").asString());
        assertTrue(disputed.data().path("disputeByOwner").asBoolean());
        var admin = world.player("Ada");

        assertFalse(perform(admin, Actions.ADMIN_JOBS_DISPUTES, "{}", Set.of()).ok());
        var list = perform(admin, Actions.ADMIN_JOBS_DISPUTES, "{}", Set.of(Permission.Admin.JOBS));
        var row = list.data().path("disputes").get(0);
        assertEquals("Olga", row.path("owner").asString());
        assertEquals("Builders", row.path("company").asString());
        assertEquals("Nothing done", row.path("reason").asString());
        var settled = perform(
                admin, Actions.ADMIN_JOBS_SETTLE, "{\"job\":" + id + ",\"percent\":40}", Set.of(Permission.Admin.JOBS));

        assertTrue(settled.ok(), settled.message());
        assertEquals(0, settled.data().path("disputes").size());
        assertEquals(JobState.SETTLED, world.jobs.job(id).orElseThrow().state());
        assertEquals(40.0, world.wallets.getOrDefault(manager, 0.0) + world.wallets.getOrDefault(worker, 0.0), 0.001);
        assertEquals(960.0, world.wallets.get(owner), 0.001);
    }

    private int posted() {
        return perform(owner, Actions.JOBS_CONTRACT, contract(), Set.of(Permission.Orders.JOBS))
                .data()
                .path("job")
                .path("id")
                .asInt();
    }

    private static String contract() {
        return "{\"title\":\"Barn\",\"description\":\"Paint the barn red\",\"price\":100,\"hours\":24}";
    }

    private static String job(int id) {
        return "{\"job\":" + id + "}";
    }

    private ActionResult perform(UUID player, String action, String args, Set<String> permissions) {
        var request = new ActionRequest(
                "action-1",
                Protocol.VERSION,
                "session",
                player,
                action,
                Protocol.mapper().readTree(args));
        var now = Times.now();
        var session = new StoredSession(
                "session", player, world.names.get(player), NODE, now, now, now, now, null, permissions);
        return world.writes.perform(request, session);
    }
}
