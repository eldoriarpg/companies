/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobHarvest;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobMaterials;
import de.eldoria.companies.data.wrapper.job.StoredArea;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.jobs.Blueprint;
import de.eldoria.companies.jobs.JobService;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
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
    private static final UUID HERE = new UUID(0, 1);
    private static final JobArea FIELD = new JobArea("world", 0, 60, 0, 9, 64, 9);

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
    void aContractIsSavedAsADraftThenPostedOrDeleted() {
        var jobs = Set.of(Permission.Orders.JOBS);
        var drafted = perform(owner, Actions.JOBS_CONTRACT, draft(contract()), jobs);

        assertTrue(drafted.ok(), drafted.message());
        var job = drafted.data().path("job");
        assertEquals("draft", job.path("state").asString());
        assertTrue(job.path("node").isMissingNode() || job.path("node").isNull());
        assertEquals(1000.0, world.wallets.get(owner), 0.001);
        var id = job.path("id").asInt();
        var edited = perform(
                owner,
                Actions.JOBS_DRAFTS_EDIT,
                "{\"id\":" + id + ",\"title\":\"Paint it blue\",\"price\":120,\"hours\":48}",
                jobs);
        assertTrue(edited.ok(), edited.message());
        assertEquals("Paint it blue", edited.data().path("job").path("title").asString());

        var posted = perform(owner, Actions.JOBS_DRAFTS_POST, "{\"id\":" + id + "}", jobs);
        assertTrue(posted.ok(), posted.message());
        assertEquals("open", posted.data().path("job").path("state").asString());
        assertTrue(world.wallets.get(owner) < 1000.0);

        var second = perform(owner, Actions.JOBS_CONTRACT, draft(contract()), jobs)
                .data()
                .path("job")
                .path("id")
                .asInt();
        assertEquals(
                "order.job.error.noDraft",
                perform(worker, Actions.JOBS_DRAFTS_DELETE, "{\"id\":" + second + "}", jobs)
                        .reason());
        assertTrue(perform(owner, Actions.JOBS_DRAFTS_DELETE, "{\"id\":" + second + "}", jobs)
                .ok());
        assertEquals(
                "order.job.error.noDraft",
                perform(owner, Actions.JOBS_DRAFTS_POST, "{\"id\":" + second + "}", jobs)
                        .reason());
    }

    @Test
    void aSuppliedDraftTakesItsMaterialsFromTheItemReturns() {
        var blueprint = Blueprint.of(
                List.of(new Blueprint.Cell(0, 0, 0, Material.STONE), new Blueprint.Cell(1, 0, 0, Material.STONE)));
        var draft = ((JobService.Posted.Done) world.jobServices
                        .service()
                        .saveDraft(
                                owner,
                                JobKind.BUILD,
                                "Wall",
                                100,
                                24,
                                world.jobServices.node(),
                                new JobArea("world", 0, 64, 0, 1, 64, 0),
                                JobService.Details.build(blueprint),
                                true))
                .job();
        var jobs = Set.of(Permission.Orders.JOBS);
        var info = perform(owner, Actions.JOBS_INFO, "{\"job\":" + draft.id() + "}", jobs);
        assertTrue(info.data().path("supplied").asBoolean());
        assertEquals(2, info.data().path("missing").path("STONE").asInt());
        world.orders
                .submitItemReturn(owner, ReturnKind.OWNER, new ItemStack(Material.STONE), 5)
                .asFuture()
                .join();

        var supplied = perform(owner, Actions.JOBS_DRAFTS_SUPPLY, "{\"id\":" + draft.id() + "}", jobs);

        assertTrue(supplied.ok(), supplied.message());
        assertTrue(supplied.data().path("missing").isEmpty());
        assertEquals(2, supplied.data().path("stock").path("STONE").asInt());
    }

    private static String draft(String args) {
        return args.substring(0, args.length() - 1) + ",\"draft\":true}";
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

    @Test
    void aContractMayBePlacedWhereItsOwnerWasLastSeen() {
        var placed = perform(
                owner,
                Actions.JOBS_CONTRACT,
                "{\"title\":\"Barn\",\"price\":100,\"hours\":24,\"place\":true}",
                Set.of(Permission.Orders.JOBS));
        var nowhere = perform(owner, Actions.JOBS_CONTRACT, contract(), Set.of(Permission.Orders.JOBS));

        assertEquals("world", placed.data().path("job").path("world").asString());
        assertEquals("4 64 4", placed.data().path("job").path("center").asString());
        assertTrue(nowhere.data().path("job").path("world").asString("").isEmpty());
    }

    @Test
    void jobsArePostedOnAStoredAreaOfThisServer() {
        world.server.addSimpleWorld("world");
        world.jobSettings.unownedWorlds(List.of("world"));
        world.jobs.saveArea(owner, new StoredArea("field", HERE, FIELD), Times.now());
        world.jobs.saveArea(owner, new StoredArea("far", UUID.randomUUID(), FIELD), Times.now());

        var listed = perform(owner, Actions.JOBS_AREAS_LIST, "{}", Set.of(Permission.Orders.JOBS));
        var posted = perform(
                owner,
                Actions.JOBS_AREAS_POST,
                "{\"area\":\"field\",\"kind\":\"terraform\",\"title\":\"Level\",\"price\":100,\"hours\":24,"
                        + "\"y\":62,\"surface\":\"grass_block\"}",
                Set.of(Permission.Orders.JOBS));
        var far = perform(owner, Actions.JOBS_AREAS_POST, onArea("far"), Set.of(Permission.Orders.JOBS));
        var unknown = perform(owner, Actions.JOBS_AREAS_POST, onArea("nowhere"), Set.of(Permission.Orders.JOBS));
        var forbidden = perform(owner, Actions.JOBS_AREAS_POST, onArea("field"), Set.of());
        var deleted = perform(owner, Actions.JOBS_AREAS_DELETE, "{\"name\":\"far\"}", Set.of(Permission.Orders.JOBS));
        var hidden = perform(owner, Actions.JOBS_AREAS_LIST, "{}", Set.of());

        assertEquals(2, listed.data().path("areas").size());
        assertEquals(
                HERE.toString(), listed.data().path("areas").get(1).path("node").asString());
        assertEquals(10, listed.data().path("areas").get(1).path("width").asInt());
        var job = world.jobs.job(posted.data().path("job").path("id").asInt()).orElseThrow();
        assertEquals(JobKind.TERRAFORM, job.kind());
        assertEquals(FIELD, job.area());
        assertEquals(62, job.target().y());
        assertEquals("GRASS_BLOCK", job.target().surface());
        assertEquals(900.0, world.wallets.get(owner), 50.0);
        assertEquals("order.job.area.error.otherServer", far.reason());
        assertEquals("order.job.area.error.unknown", unknown.reason());
        assertEquals("error.permission.postJob", forbidden.reason());
        assertEquals(1, deleted.data().path("areas").size());
        assertEquals("error.permission.postJob", hidden.reason());
    }

    @Test
    void anAreaJobCountsWhatItDigsAndFillsAndTakesItsMaterialChoices() {
        world.server.addSimpleWorld("world");
        world.jobSettings.unownedWorlds(List.of("world"));
        world.jobs.saveArea(owner, new StoredArea("field", HERE, FIELD), Times.now());
        var terraform = "\"area\":\"field\",\"kind\":\"terraform\",\"y\":62";

        var counted =
                perform(owner, Actions.JOBS_AREAS_MATERIALS, "{" + terraform + "}", Set.of(Permission.Orders.JOBS));
        var posted = perform(
                owner,
                Actions.JOBS_AREAS_POST,
                "{" + terraform + ",\"title\":\"Level\",\"price\":100,\"hours\":24,\"fill\":\"dirt\","
                        + "\"dugToStock\":true}",
                Set.of(Permission.Orders.JOBS));
        world.jobSettings.suppliedFill(false);
        var refused = perform(
                owner,
                Actions.JOBS_AREAS_POST,
                "{" + terraform + ",\"title\":\"Level\",\"price\":100,\"hours\":24,\"fill\":\"dirt\"}",
                Set.of(Permission.Orders.JOBS));

        assertEquals(300, counted.data().path("fillBlocks").asInt());
        assertEquals(0, counted.data().path("dugBlocks").asInt());
        assertTrue(counted.data().path("suppliedFill").asBoolean());
        var draft = world.jobs.job(posted.data().path("job").path("id").asInt()).orElseThrow();
        assertEquals(JobState.DRAFT, draft.state());
        assertTrue(draft.supplied());
        assertEquals(new JobMaterials(true, "DIRT"), draft.materials());
        assertEquals(300, posted.data().path("missing").path("DIRT").asInt());
        assertEquals("order.job.error.choice", refused.reason());
    }

    @Test
    void areaJobsAreRefusedOnUnownedLandOrOutsideTheWorld() {
        world.server.addSimpleWorld("world");
        world.jobs.saveArea(owner, new StoredArea("field", HERE, FIELD), Times.now());
        world.jobs.saveArea(
                owner, new StoredArea("sky", HERE, new JobArea("world", 0, 500, 0, 1, 501, 1)), Times.now());

        var unowned = perform(owner, Actions.JOBS_AREAS_POST, onArea("field"), Set.of(Permission.Orders.JOBS));
        var sky = perform(owner, Actions.JOBS_AREAS_POST, onArea("sky"), Set.of(Permission.Orders.JOBS));
        var kind = perform(
                owner,
                Actions.JOBS_AREAS_POST,
                "{\"area\":\"field\",\"kind\":\"build\",\"title\":\"Barn\",\"price\":100,\"hours\":24}",
                Set.of(Permission.Orders.JOBS));

        world.jobSettings.unownedWorlds(List.of("world"));
        world.jobSettings.digMaxVolume(100);
        var large = perform(owner, Actions.JOBS_AREAS_POST, onArea("field"), Set.of(Permission.Orders.JOBS));
        var noY = perform(
                owner,
                Actions.JOBS_AREAS_POST,
                "{\"area\":\"field\",\"kind\":\"terraform\",\"title\":\"Level\",\"price\":100,\"hours\":24}",
                Set.of(Permission.Orders.JOBS));

        assertEquals("order.job.error.unowned", unowned.reason());
        assertEquals("order.job.error.height", sky.reason());
        assertEquals("order.job.error.volume", large.reason());
        assertEquals(100, JobService.limit(JobService.Refusal.VOLUME, JobKind.DIG, world.jobSettings));
        assertEquals("dashboard.unsupported", noY.reason());
        assertEquals("dashboard.unsupported", kind.reason());
        assertTrue(world.jobs.ofOwner(owner, 10).isEmpty());
    }

    @Test
    void previewsAndBuildsOnAStoredAreaAreRefusedWithoutWhatTheyNeed() {
        world.jobs.saveArea(owner, new StoredArea("field", HERE, FIELD), Times.now());
        world.jobs.saveArea(
                owner, new StoredArea("huge", HERE, new JobArea("world", 0, 60, 0, 300, 64, 9)), Times.now());
        var jobs = Set.of(Permission.Orders.JOBS);

        var huge = perform(owner, Actions.JOBS_AREAS_PREVIEW, "{\"area\":\"huge\"}", jobs);
        var noWorld = perform(owner, Actions.JOBS_AREAS_PREVIEW, "{\"area\":\"field\"}", jobs);
        var rotation = perform(
                owner,
                Actions.JOBS_AREAS_SCHEMATIC,
                "{\"area\":\"field\",\"schematic\":\"barn\",\"rotation\":45}",
                jobs);
        var missing = perform(
                owner,
                Actions.JOBS_AREAS_SCHEMATIC,
                "{\"area\":\"field\",\"schematic\":\"barn\",\"rotation\":90}",
                jobs);
        var build = perform(
                owner,
                Actions.JOBS_AREAS_BUILD,
                "{\"area\":\"field\",\"schematic\":\"barn\",\"rotation\":0,\"x\":0,\"z\":0,\"title\":\"Barn\","
                        + "\"price\":100,\"hours\":24}",
                jobs);
        var forbidden = perform(owner, Actions.JOBS_AREAS_PREVIEW, "{\"area\":\"field\"}", Set.of());

        assertEquals("order.job.area.error.tooLarge", huge.reason());
        assertEquals("order.job.error.noWorld", noWorld.reason());
        assertEquals("order.job.error.noBlueprints", rotation.reason());
        assertEquals("order.job.error.noBlueprints", missing.reason());
        assertEquals("order.job.error.noBlueprints", build.reason());
        assertEquals("error.permission.postJob", forbidden.reason());
    }

    @Test
    void aContractMayBePlacedAtAStoredAreaOfAnyServer() {
        var other = UUID.randomUUID();
        world.jobs.saveArea(owner, new StoredArea("field", other, FIELD), Times.now());

        var placed = perform(
                owner,
                Actions.JOBS_CONTRACT,
                "{\"title\":\"Barn\",\"price\":100,\"hours\":24,\"place\":true,\"area\":\"field\"}",
                Set.of(Permission.Orders.JOBS));
        var unknown = perform(
                owner,
                Actions.JOBS_CONTRACT,
                "{\"title\":\"Barn\",\"price\":100,\"hours\":24,\"area\":\"nowhere\"}",
                Set.of(Permission.Orders.JOBS));

        var job = world.jobs.job(placed.data().path("job").path("id").asInt()).orElseThrow();
        assertEquals(FIELD, job.area());
        assertEquals(other, job.node());
        assertEquals("order.job.area.error.unknown", unknown.reason());
    }

    @Test
    void takingIsOfferedOnlyWithAFreeOrderSlot() {
        var id = posted();
        assertTrue(perform(manager, Actions.JOBS_INFO, job(id), Set.of())
                .data()
                .path("takeable")
                .asBoolean());

        world.companySlots = 0;

        assertFalse(perform(manager, Actions.JOBS_INFO, job(id), Set.of())
                .data()
                .path("takeable")
                .asBoolean());
    }

    @Test
    void theOwnerCollectsAHarvestStockIntoTheirItemReturns() {
        var id = world.jobs
                .create(Job.draft(
                                owner,
                                JobKind.HARVEST,
                                "Field",
                                50,
                                5,
                                NODE,
                                new JobArea("world", 0, 60, 0, 4, 61, 4),
                                JobLand.REGION,
                                "home",
                                24,
                                Times.now(),
                                Times.now().plusDays(7))
                        .withHarvest(new JobHarvest(1, 0)))
                .orElseThrow();
        world.jobs.stock(id, Map.of("WHEAT", 12));

        assertFalse(perform(manager, Actions.JOBS_COLLECT, job(id), Set.of()).ok());
        var collected = perform(owner, Actions.JOBS_COLLECT, job(id), Set.of());

        assertTrue(collected.ok(), collected.message());
        assertEquals(Map.of("WHEAT", 12), world.returned.get(owner));
        assertEquals(Map.of(), world.jobs.stock(id));
        assertFalse(perform(owner, Actions.JOBS_COLLECT, job(id), Set.of()).ok());
    }

    @Test
    void aStockThatCanNotBeStoredStaysAndOtherJobsHaveNone() {
        var id = world.jobs
                .create(Job.draft(
                                owner,
                                JobKind.HARVEST,
                                "Field",
                                50,
                                5,
                                NODE,
                                new JobArea("world", 0, 60, 0, 4, 61, 4),
                                JobLand.REGION,
                                "home",
                                24,
                                Times.now(),
                                Times.now().plusDays(7))
                        .withHarvest(new JobHarvest(1, 0)))
                .orElseThrow();
        world.jobs.stock(id, Map.of("WHEAT", 12));
        world.returnsFail = true;

        var failed = perform(owner, Actions.JOBS_COLLECT, job(id), Set.of());
        var contract = perform(owner, Actions.JOBS_COLLECT, job(posted()), Set.of());

        assertFalse(failed.ok());
        assertEquals("order.job.error.failed", failed.reason());
        assertEquals(Map.of("WHEAT", 12), world.jobs.stock(id));
        assertFalse(contract.ok());
        assertEquals("order.job.error.unknown", contract.reason());
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

    private static String onArea(String area) {
        return "{\"area\":\"" + area + "\",\"kind\":\"dig\",\"title\":\"Dig\",\"price\":100,\"hours\":24}";
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
