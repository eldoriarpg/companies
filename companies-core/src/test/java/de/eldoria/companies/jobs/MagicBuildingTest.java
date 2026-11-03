/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import com.google.common.util.concurrent.MoreExecutors;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteJobData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobMaterials;
import de.eldoria.companies.data.wrapper.job.JobTarget;
import de.eldoria.companies.jobs.hooks.Claim;
import de.eldoria.companies.jobs.hooks.JobHooks;
import de.eldoria.companies.jobs.hooks.JobLandHook;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import net.kyori.adventure.text.Component;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Building a supplied job from its stock: a magic block places exactly what the blueprint asks for and takes it from
 * the stock, refuses wrong and finished positions, places both halves of a door at once, and a block placed so stays
 * counted when anyone but the company breaks it, while a member breaking it puts the item back. Nothing but magic blocks
 * and the whitelist builds in the area, and magic blocks are taken from players who left it.
 */
class MagicBuildingTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String STAIRS =
            "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]";
    private static final String DOOR_LOWER =
            "minecraft:oak_door[facing=east,half=lower,hinge=left,open=false,powered=false]";
    private static final String DOOR_UPPER =
            "minecraft:oak_door[facing=east,half=upper,hinge=left,open=false,powered=false]";

    @TempDir
    Path directory;

    private final Map<Integer, Set<UUID>> members = new HashMap<>();
    private final List<UUID> onlyMagic = new ArrayList<>();
    private ServerMock server;
    private WorldMock world;
    private SqLiteJobData jobs;
    private JobAccess access;
    private JobStocks stocks;
    private MagicItems items;
    private MagicBuilding building;
    private Player worker;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        var plugin = MockBukkit.createMockPlugin();
        var messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        SqLiteTestDatabase.create(directory);
        jobs = new SqLiteJobData();
        var hooks = new JobHooks(name -> false, className -> new Object(), Logger.getAnonymousLogger());
        hooks.load();
        hooks.register(new OpenLand());
        var blueprints = new Blueprints(jobs, Logger.getAnonymousLogger());
        access = new JobAccess(
                jobs,
                company -> members.getOrDefault(company, Set.of()),
                hooks,
                NODE,
                Runnable::run,
                job -> {},
                job -> CompletableFuture.completedFuture(null),
                (job, company, byWorker) -> {},
                blueprints,
                Logger.getAnonymousLogger());
        items = new MagicItems(plugin);
        access.building(() -> Set.of(Material.SCAFFOLDING), (player, want) -> {});
        access.supplying(player -> onlyMagic.add(player.getUniqueId()));
        stocks = new JobStocks(jobs, Logger.getAnonymousLogger(), MoreExecutors.newDirectExecutorService());
        building = new MagicBuilding(plugin, access, blueprints, stocks, items, new BuildGhosts(plugin), messages);
        worker = server.addPlayer();
        worker.teleport(world.getBlockAt(5, 61, 5).getLocation());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMagicBlockPlacesWhatTheBlueprintAsksFromTheStockOncePerPosition() {
        var id = suppliedJob(Map.of("OAK_STAIRS", 1, "STONE", 1));
        var spot = world.getBlockAt(0, 60, 0);

        building.place(worker, spot, magic(id, Material.STONE), EquipmentSlot.HAND);
        building.place(worker, spot, magic(id, Material.OAK_STAIRS), EquipmentSlot.HAND);
        building.place(worker, spot, magic(id, Material.OAK_STAIRS), EquipmentSlot.HAND);
        access.flush();

        assertEquals(STAIRS, spot.getBlockData().getAsString());
        assertEquals(Map.of("STONE", 1), jobs.stock(id));
        assertTrue(access.counted(id, JobAccess.key(spot)));
        assertEquals(Map.of(worker.getUniqueId(), 1), jobs.workOf(id));
    }

    @Test
    void aBlockBrokenByAnyoneElseStaysCountedAndAMemberPutsItBack() {
        var id = suppliedJob(Map.of("OAK_STAIRS", 1, "STONE", 1));
        var stairs = world.getBlockAt(0, 60, 0);
        var stone = world.getBlockAt(1, 60, 0);
        building.place(worker, stairs, magic(id, Material.OAK_STAIRS), EquipmentSlot.HAND);
        building.place(worker, stone, magic(id, Material.STONE), EquipmentSlot.HAND);
        var owner = server.addPlayer();

        var undo = breaking(stairs, owner);
        stairs.setType(Material.AIR);
        var back = breaking(stone, worker);

        assertFalse(undo.isCancelled());
        assertTrue(undo.isDropItems());
        assertTrue(access.counted(id, JobAccess.key(stairs)));
        var wanted = new Blueprint.Want(Material.OAK_STAIRS, STAIRS);
        assertTrue(
                BuildCheck.counting(BuildCheck.of(world), id, access::counted).matches(wanted, 0, 60, 0));
        assertFalse(back.isDropItems());
        assertFalse(access.counted(id, JobAccess.key(stone)));
        assertEquals(Map.of("STONE", 1), jobs.stock(id));
    }

    @Test
    void bothHalvesOfADoorArePlacedForOneItem() {
        var id = doorJob();

        building.place(worker, world.getBlockAt(0, 60, 0), magic(id, Material.OAK_DOOR), EquipmentSlot.HAND);

        assertEquals(DOOR_LOWER, world.getBlockAt(0, 60, 0).getBlockData().getAsString());
        assertEquals(DOOR_UPPER, world.getBlockAt(0, 61, 0).getBlockData().getAsString());
        assertEquals(Map.of(), jobs.stock(id));
        assertTrue(access.counted(id, JobAccess.key(world.getBlockAt(0, 61, 0))));
    }

    @Test
    void onlyMagicBlocksAndTheWhitelistBuildInTheArea() {
        suppliedJob(Map.of("STONE", 1));

        var stone = placing(world.getBlockAt(1, 60, 0), Material.STONE);
        var scaffolding = placing(world.getBlockAt(1, 60, 0), Material.SCAFFOLDING);

        assertTrue(stone.isCancelled());
        assertFalse(scaffolding.isCancelled());
        assertEquals(List.of(worker.getUniqueId()), onlyMagic);
    }

    @Test
    void aMemberPicksOneMagicBlockAndLosesItAwayFromTheArea() {
        var id = suppliedJob(Map.of("OAK_STAIRS", 1));

        building.pick(worker, id, Material.STONE);
        building.pick(worker, id, Material.OAK_STAIRS);
        var held = items.magic(worker.getInventory().getItemInMainHand());
        worker.teleport(world.getBlockAt(100, 61, 100).getLocation());
        building.sweep();

        assertEquals(List.of(Material.OAK_STAIRS, Material.STONE), building.types(id));
        assertEquals(new MagicItems.Magic(id, Material.OAK_STAIRS), held.orElseThrow());
        assertFalse(items.isMagic(worker.getInventory().getItemInMainHand()));
    }

    @Test
    void aDoorBrokenAtItsUpperHalfGoesBackOnce() {
        var id = doorJob();
        building.place(worker, world.getBlockAt(0, 60, 0), magic(id, Material.OAK_DOOR), EquipmentSlot.HAND);

        var back = breaking(world.getBlockAt(0, 61, 0), worker);

        assertFalse(back.isDropItems());
        assertEquals(Map.of("OAK_DOOR", 1), jobs.stock(id));
        assertFalse(access.counted(id, JobAccess.key(world.getBlockAt(0, 60, 0))));
    }

    @Test
    void aMemberBreakingABlockTheBlueprintAsksForGivesItToTheStockWithoutDropOrExperience() {
        var id = suppliedJob(Map.of());
        world.getBlockAt(1, 60, 0).setType(Material.STONE);
        world.getBlockAt(0, 60, 0).setType(Material.DIRT);

        var asked = breaking(world.getBlockAt(1, 60, 0), worker);
        var other = breaking(world.getBlockAt(0, 60, 0), worker);

        assertFalse(asked.isDropItems());
        assertEquals(0, asked.getExpToDrop());
        assertTrue(other.isDropItems());
        assertEquals(Map.of("STONE", 1), jobs.stock(id));
    }

    @Test
    void aBreakGivesBackOnlyOnceItWentThrough() {
        var id = suppliedJob(Map.of("STONE", 1));
        var stone = world.getBlockAt(1, 60, 0);
        building.place(worker, stone, magic(id, Material.STONE), EquipmentSlot.HAND);

        building.onBreak(new BlockBreakEvent(stone, worker));

        assertTrue(access.counted(id, JobAccess.key(stone)));
        assertEquals(Map.of(), jobs.stock(id));
    }

    @Test
    void aBlockFromTheStockThatPopsOffGoesBackWithoutADrop() {
        var id = suppliedJob(Map.of("STONE", 1));
        var stone = world.getBlockAt(1, 60, 0);
        building.place(worker, stone, magic(id, Material.STONE), EquipmentSlot.HAND);

        var destroyed =
                new BlockDestroyEvent(stone, Material.AIR.createBlockData(), Material.STONE.createBlockData(), 0, true);
        building.onDestroy(destroyed);

        assertFalse(destroyed.willDrop());
        assertFalse(access.counted(id, JobAccess.key(stone)));
        assertEquals(Map.of("STONE", 1), jobs.stock(id));
    }

    @Test
    void explosionsLeaveBlocksFromTheStockStanding() {
        var id = suppliedJob(Map.of("STONE", 1));
        var stone = world.getBlockAt(1, 60, 0);
        building.place(worker, stone, magic(id, Material.STONE), EquipmentSlot.HAND);
        List<Block> blocks = new ArrayList<>(List.of(stone, world.getBlockAt(3, 60, 0)));

        building.onBlockExplode(new BlockExplodeEvent(
                world.getBlockAt(3, 60, 0), world.getBlockAt(3, 60, 0).getState(), blocks, 1, ExplosionResult.DESTROY));

        assertEquals(List.of(world.getBlockAt(3, 60, 0)), blocks);
    }

    @Test
    void aClosedStockTakesAndGivesNothing() {
        var id = suppliedJob(Map.of("STONE", 1));

        stocks.close(id);

        assertFalse(stocks.take(id, Map.of("STONE", 1)));
        assertFalse(stocks.give(id, Map.of("STONE", 1)));
        assertEquals(Map.of("STONE", 1), jobs.stock(id));
    }

    @Test
    void theJobChestGoesWhereTheBlueprintAsksForNothing() {
        var id = suppliedJob(
                Blueprint.of(List.of(new Blueprint.Cell(0, 0, 0, Material.STONE))),
                new JobArea("world", 0, 60, 0, 1, 60, 0),
                Map.of("STONE", 1));
        var asked = world.getBlockAt(0, 60, 0);
        var free = world.getBlockAt(1, 60, 0);

        building.placeChest(worker, asked, id, null);
        building.placeChest(worker, free, id, null);

        assertTrue(asked.getType().isAir());
        assertEquals(MagicItems.CHEST, free.getType());
    }

    @Test
    void magicBlocksStayInTheirHoldersOwnInventory() {
        var id = suppliedJob(Map.of("STONE", 1));
        building.pick(worker, id, Material.STONE);
        var chest = server.createInventory(null, 27);
        var into = worker.openInventory(chest);
        var click = new InventoryClickEvent(
                into,
                InventoryType.SlotType.CONTAINER,
                30,
                ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);
        click.setCurrentItem(worker.getInventory().getItemInMainHand());

        assertTrue(building.moves(click, worker));
    }

    @Test
    void aPlacingAnotherPluginCancelsIsTakenBackWithItsItems() {
        var id = suppliedJob(Map.of("STONE", 1));
        var stone = world.getBlockAt(1, 60, 0);
        server.getPluginManager()
                .registerEvents(
                        new Listener() {
                            @EventHandler
                            public void deny(BlockPlaceEvent event) {
                                event.setCancelled(true);
                            }
                        },
                        MockBukkit.createMockPlugin());

        building.place(worker, stone, magic(id, Material.STONE), EquipmentSlot.HAND);

        assertTrue(stone.getType().isAir());
        assertFalse(access.counted(id, JobAccess.key(stone)));
        assertEquals(Map.of("STONE", 1), jobs.stock(id));
    }

    @Test
    void fluidsDecayAndCreaturesLeaveBlocksFromTheStock() {
        var id = suppliedJob(Map.of("STONE", 1));
        var stone = world.getBlockAt(1, 60, 0);
        building.place(worker, stone, magic(id, Material.STONE), EquipmentSlot.HAND);

        var flow = new BlockFromToEvent(world.getBlockAt(2, 60, 0), stone);
        building.onFlow(flow);
        var decay = new LeavesDecayEvent(stone);
        building.onDecay(decay);

        assertTrue(flow.isCancelled());
        assertTrue(decay.isCancelled());
    }

    @Test
    void theWhitelistGoesNowhereABlockFromTheStockCounts() {
        var id = suppliedJob(Map.of("STONE", 1));
        var stone = world.getBlockAt(1, 60, 0);
        building.place(worker, stone, magic(id, Material.STONE), EquipmentSlot.HAND);
        stone.setType(Material.AIR);

        var scaffolding = placing(stone, Material.SCAFFOLDING);

        assertTrue(scaffolding.isCancelled());
        assertTrue(access.counted(id, JobAccess.key(stone)));
    }

    @Test
    void theFirstLayerIsBuiltAgainstTheGroundBelowTheArea() {
        var id = suppliedJob(Map.of("STONE", 1));
        var ground = world.getBlockAt(1, 59, 0);
        ground.setType(Material.DIRT);
        var held = items.magic(id, Material.STONE, Component.text("Stone"), List.of());
        worker.getInventory().setItemInMainHand(held);

        var use = new PlayerInteractEvent(
                worker, Action.RIGHT_CLICK_BLOCK, held, ground, BlockFace.UP, EquipmentSlot.HAND);
        access.onUse(use);
        building.onUse(use);

        assertEquals(Material.STONE, world.getBlockAt(1, 60, 0).getType());
        assertEquals(Map.of(), jobs.stock(id));
    }

    @Test
    void aSuppliedTerraformJobKeepsItsFillAndBuildsItWithMagicBlocksOnly() {
        var area = new JobArea("world", 0, 60, 0, 1, 60, 0);
        var fill = AreaMaterials.of(
                        JobKind.TERRAFORM,
                        area,
                        new JobTarget(60, null),
                        Material.DIRT,
                        (x, y, z) -> x == 0 ? Material.AIR : Material.STONE)
                .blueprint(area);
        var id = suppliedJob(JobKind.TERRAFORM, fill, area, Map.of("DIRT", 1));
        access.check();

        var own = placing(world.getBlockAt(0, 60, 0), Material.DIRT);
        world.getBlockAt(0, 60, 0).setType(Material.AIR);
        building.place(worker, world.getBlockAt(0, 60, 0), magic(id, Material.DIRT), EquipmentSlot.HAND);

        assertTrue(own.isCancelled());
        assertEquals(Material.DIRT, world.getBlockAt(0, 60, 0).getType());
        assertEquals(Map.of(), jobs.stock(id));
        assertTrue(access.counted(id, JobAccess.key(world.getBlockAt(0, 60, 0))));
    }

    @Test
    void theBuildersToolPlacesWhatTheBlueprintAsksWhileTheCompanyMayUseThePerk() {
        Set<Integer> perk = new HashSet<>(Set.of(3));
        building.tools(company -> CompletableFuture.completedFuture(perk.contains(company)));
        var id = suppliedJob(Map.of("OAK_STAIRS", 1, "STONE", 1));
        var stairs = world.getBlockAt(0, 60, 0);
        var stone = world.getBlockAt(1, 60, 0);

        building.build(worker, stone, 4, EquipmentSlot.HAND);
        building.build(worker, stairs, 3, EquipmentSlot.HAND);
        perk.clear();
        building.tools(company -> CompletableFuture.completedFuture(perk.contains(company)));
        building.sweep();
        building.build(worker, stone, 3, EquipmentSlot.HAND);

        assertEquals(STAIRS, stairs.getBlockData().getAsString());
        assertTrue(stone.getType().isAir());
        assertEquals(Map.of("STONE", 1), jobs.stock(id));
    }

    @Test
    void theBuildersToolAsksAtOnceForACompanyThatStartedWorkingBetweenChecks() {
        building.tools(company -> CompletableFuture.completedFuture(true));
        suppliedJob(Map.of("STONE", 1));
        var builder = server.addPlayer();
        builder.teleport(world.getBlockAt(25, 61, 5).getLocation());
        var area = new JobArea("world", 20, 60, 0, 20, 60, 0);
        var id = jobs.create(Job.draft(
                                UUID.randomUUID(),
                                JobKind.BUILD,
                                "Post",
                                100,
                                5,
                                NODE,
                                area,
                                JobLand.AREA,
                                "plotsquared:plots|1;1",
                                24,
                                NOW,
                                NOW.plusDays(7))
                        .withSupplied(true))
                .orElseThrow();
        jobs.blueprint(
                id,
                Blueprint.of(List.of(new Blueprint.Cell(0, 0, 0, Material.STONE)))
                        .encode());
        jobs.stock(id, Map.of("STONE", 1));
        jobs.take(id, 4, NOW, NOW.plusDays(1));
        members.put(4, Set.of(builder.getUniqueId()));
        access.check();
        building.sweep();
        var post = world.getBlockAt(20, 60, 0);

        building.build(builder, post, 4, EquipmentSlot.HAND);
        assertTrue(post.getType().isAir());
        building.build(builder, post, 4, EquipmentSlot.HAND);

        assertEquals(Material.STONE, post.getType());
        assertEquals(Map.of(), jobs.stock(id));
    }

    @Test
    void theBuildersToolPoursFluidsAndPlacesDoorsWholeButNotForStrangers() {
        building.tools(company -> CompletableFuture.completedFuture(true));
        var id = suppliedJob(
                Blueprint.of(List.of(
                        new Blueprint.Cell(0, 0, 0, Material.OAK_DOOR, DOOR_LOWER),
                        new Blueprint.Cell(0, 1, 0, Material.OAK_DOOR, DOOR_UPPER),
                        new Blueprint.Cell(1, 0, 0, Material.WATER, "minecraft:water[level=0]"),
                        new Blueprint.Cell(1, 1, 0, Material.STONE))),
                new JobArea("world", 0, 60, 0, 1, 61, 0),
                Map.of("OAK_DOOR", 1, "WATER_BUCKET", 2, "STONE", 1));
        building.sweep();
        var stranger = server.addPlayer();
        stranger.teleport(world.getBlockAt(5, 61, 5).getLocation());

        building.build(stranger, world.getBlockAt(1, 61, 0), 3, EquipmentSlot.HAND);
        building.build(worker, world.getBlockAt(0, 61, 0), 3, EquipmentSlot.HAND);
        building.build(worker, world.getBlockAt(0, 60, 0), 3, EquipmentSlot.HAND);
        building.build(worker, world.getBlockAt(1, 60, 0), 3, EquipmentSlot.HAND);

        assertTrue(world.getBlockAt(1, 61, 0).getType().isAir());
        assertEquals(DOOR_UPPER, world.getBlockAt(0, 61, 0).getBlockData().getAsString());
        assertEquals(Material.WATER, world.getBlockAt(1, 60, 0).getType());
        assertEquals(Map.of("STONE", 1, "WATER_BUCKET", 2), jobs.stock(id));
    }

    @Test
    void aBuildersToolStaysOutOfOtherInventoriesAndGoesWhenDropped() {
        suppliedJob(Map.of("STONE", 1));
        var tool = items.tool(3, Component.text("Tool"), List.of());
        worker.getInventory().setItemInMainHand(tool);
        var into = worker.openInventory(server.createInventory(null, 27));
        var click = new InventoryClickEvent(
                into,
                InventoryType.SlotType.CONTAINER,
                30,
                ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);
        click.setCurrentItem(tool);
        var dropped = world.dropItem(worker.getLocation(), tool);
        var drop = new PlayerDropItemEvent(worker, dropped);

        building.onDrop(drop);

        assertTrue(building.moves(click, worker));
        assertFalse(drop.isCancelled());
        assertFalse(dropped.isValid());
    }

    @Test
    void magicItemsAndJobChestsKnowTheirJob() {
        var tool = items.tool(3, Component.text("Tool"), List.of());
        assertEquals(3, items.tool(tool).orElseThrow());
        assertTrue(items.magic(tool).isEmpty());
        var magic = items.magic(7, Material.STONE, Component.text("Stone"), List.of());
        var chest = items.chest(7, Component.text("Chest"), List.of());

        assertEquals(new MagicItems.Magic(7, Material.STONE), items.magic(magic).orElseThrow());
        assertEquals(7, items.chest(chest).orElseThrow());
        assertTrue(items.magic(new ItemStack(Material.STONE)).isEmpty());
        assertTrue(items.chest(magic).isEmpty());
    }

    private BlockBreakEvent breaking(Block block, Player player) {
        var event = new BlockBreakEvent(block, player);
        access.onHold(event);
        if (!event.isCancelled()) building.onBreak(event);
        if (!event.isCancelled()) building.onBroken(event);
        return event;
    }

    private int doorJob() {
        return suppliedJob(
                Blueprint.of(List.of(
                        new Blueprint.Cell(0, 0, 0, Material.OAK_DOOR, DOOR_LOWER),
                        new Blueprint.Cell(0, 1, 0, Material.OAK_DOOR, DOOR_UPPER))),
                new JobArea("world", 0, 60, 0, 0, 61, 0),
                Map.of("OAK_DOOR", 1));
    }

    private MagicItems.Magic magic(int job, Material type) {
        return new MagicItems.Magic(job, type);
    }

    private BlockPlaceEvent placing(Block block, Material type) {
        block.setType(type);
        var event = new BlockPlaceEvent(
                block, block.getState(), block, new ItemStack(type), worker, true, EquipmentSlot.HAND);
        access.onPlace(event);
        return event;
    }

    private int suppliedJob(Map<String, Integer> stock) {
        return suppliedJob(
                Blueprint.of(List.of(
                        new Blueprint.Cell(0, 0, 0, Material.OAK_STAIRS, STAIRS),
                        new Blueprint.Cell(1, 0, 0, Material.STONE))),
                new JobArea("world", 0, 60, 0, 1, 60, 0),
                stock);
    }

    private int suppliedJob(Blueprint blueprint, JobArea area, Map<String, Integer> stock) {
        return suppliedJob(JobKind.BUILD, blueprint, area, stock);
    }

    private int suppliedJob(JobKind kind, Blueprint blueprint, JobArea area, Map<String, Integer> stock) {
        var draft = Job.draft(
                        UUID.randomUUID(),
                        kind,
                        "Stairs",
                        100,
                        5,
                        NODE,
                        area,
                        JobLand.AREA,
                        "plotsquared:plots|1;1",
                        24,
                        NOW,
                        NOW.plusDays(7))
                .withSupplied(true);
        if (kind == JobKind.TERRAFORM) {
            draft = draft.withTarget(new JobTarget(area.maxY(), null))
                    .withMaterials(new JobMaterials(false, Material.DIRT.name()));
        }
        var id = jobs.create(draft).orElseThrow();
        jobs.blueprint(id, blueprint.encode());
        jobs.stock(id, stock);
        jobs.take(id, 3, NOW, NOW.plusDays(1));
        members.put(3, Set.of(worker.getUniqueId()));
        access.check();
        building.sweep();
        assertEquals(JobState.TAKEN, jobs.job(id).orElseThrow().state());
        return id;
    }

    /**
     * Land that is always the owner's and lets every job's company in.
     */
    private static final class OpenLand implements JobLandHook {
        @Override
        public String plugin() {
            return "Test";
        }

        @Override
        public Claim claim(OfflinePlayer owner, JobArea area) {
            return new Claim.None();
        }

        @Override
        public Set<UUID> grant(Job job, Collection<UUID> players, Set<UUID> granted) {
            return Set.copyOf(players);
        }

        @Override
        public boolean revoke(Job job, Set<UUID> granted) {
            return true;
        }

        @Override
        public boolean owns(Job job) {
            return true;
        }

        @Override
        public boolean reaches(Job job, String world, int x, int y, int z) {
            return true;
        }
    }
}
