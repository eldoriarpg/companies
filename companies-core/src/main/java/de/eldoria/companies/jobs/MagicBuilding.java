/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.dashboard.views.PlainText;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.Values;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.logging.Level;

/**
 * Builds supplied build jobs from their stock.
 * <p>
 * A member of the working company places the job chest, which stands as a barrel, inside the job's area where the
 * blueprint asks for nothing. Using it opens the picker: every item type the blueprint takes, with what the stock still
 * holds, those used up greyed out. A click gives the member one magic block of that type in place of the job's magic
 * blocks they held. Using a magic block on a block puts the block the blueprint asks for at the position it would be
 * placed at, turned and connected exactly as the schematic has it, takes its items from the stock and counts the
 * position as placed and as the member's work; the magic block stays in the hand. Other plugins are told of the
 * placement as a {@link Placing}, and one that cancels it takes the block back and its items back to the stock. A
 * door, bed or tall plant is placed whole from its main half. A magic block used where the blueprint asks for another
 * type, where the block is already right, where something stands, where a protection denied the use or outside the
 * job's area is refused with the reason, and one whose type the stock ran out of is taken away. Magic water pours a
 * source wherever the schematic has one, once the stock holds the two water buckets; magic lava takes a lava bucket
 * per source, and members can not fill buckets there.
 * <p>
 * A member breaking a block the blueprint asks for, or one placed from the stock whatever it turned into, gets no drop
 * and no experience: its items go back into the stock once the break went through, and the position is open again.
 * Anyone else breaking a block placed from the stock leaves the position counted as placed and gets the drop. A block
 * placed from the stock that pops off goes back into the stock without a drop; explosions, pistons, flowing fluids,
 * leaf decay, falling and creatures leave such blocks where they are.
 * <p>
 * A company that owns the {@value #TOOL_PERK} perk builds without picking: its builder's tool places whatever the
 * blueprint asks for at the position it is used on, from the stock, as a magic block of that type would, and pours the
 * fluid sources of the schematic. Whether the company may use the perk is asked every {@value #TOOL_SECONDS} seconds
 * for the companies working here, and at once for one not asked yet.
 * <p>
 * Magic blocks, job chest items and builder's tools are worth nothing anywhere else, as {@link MagicItems} makes them.
 * Magic blocks and job chest items never leave their holder: they can not be dropped or put into another inventory, and
 * magic blocks are taken away on death and on quitting, and every second from players more than {@value #MARGIN}
 * blocks away from the job's area, no longer in its company, or whose job is no longer worked on. A builder's tool
 * can not be stored either; dropping it removes it, and so does death, as {@code /company job tool} gives it again.
 * Only the main hand builds when both hands hold such an item. A job chest breaks without a drop and takes no items
 * from hoppers. One whose job is no longer worked on here goes away when the job does, or when it is used.
 */
public final class MagicBuilding implements Listener {
    /**
     * How far from a job's area its magic blocks may be held, in blocks, so its edges can be built from outside.
     */
    public static final int MARGIN = 8;

    /**
     * How often held magic blocks are checked, in ticks.
     */
    public static final long SWEEP_TICKS = 20;

    /**
     * The perk that gives a company the builder's tool.
     */
    public static final String TOOL_PERK = "builder-tool";

    /**
     * What the builder's tool perk costs unless {@code perks.yml} says otherwise.
     */
    public static final double TOOL_PRICE = 5000;

    /**
     * The company level from which the builder's tool perk can be bought unless {@code perks.yml} says otherwise.
     */
    public static final int TOOL_LEVEL = 2;

    /**
     * How often it is asked whether the companies working here may use the builder's tool, in seconds.
     */
    public static final int TOOL_SECONDS = 10;

    private static final int SIZE = 54;
    private static final int PAGE = 45;
    private static final int PREVIOUS = 45;
    private static final int NEXT = 53;
    private static final int WATER_BUCKETS = 2;

    private final Plugin plugin;
    private final JobAccess access;
    private final Blueprints blueprints;
    private final JobStocks stocks;
    private final MagicItems items;
    private final BuildGhosts ghosts;
    private final MessageSender messages;
    private final Map<Integer, Set<Block>> chests = new ConcurrentHashMap<>();
    private volatile Map<Integer, JobAccess.Active> swept = Map.of();
    private final Map<Integer, Boolean> tools = new ConcurrentHashMap<>();
    private final Set<Integer> asking = ConcurrentHashMap.newKeySet();
    private final AtomicInteger generation = new AtomicInteger();
    private volatile IntFunction<CompletableFuture<Boolean>> toolPerk =
            company -> CompletableFuture.completedFuture(false);
    private long toolsAsked;

    /**
     * @param plugin     the plugin
     * @param access     the jobs worked on in this server's areas and the positions they count
     * @param blueprints the blueprints of build jobs
     * @param stocks     the stock of the supplied jobs worked on here
     * @param items      reads and makes job chests and magic blocks
     * @param ghosts     the ghost blocks players see, refreshed as blocks are placed
     * @param messages   renders what players are told
     */
    public MagicBuilding(
            Plugin plugin,
            JobAccess access,
            Blueprints blueprints,
            JobStocks stocks,
            MagicItems items,
            BuildGhosts ghosts,
            MessageSender messages) {
        this.plugin = plugin;
        this.access = access;
        this.blueprints = blueprints;
        this.stocks = stocks;
        this.items = items;
        this.ghosts = ghosts;
        this.messages = messages;
    }

    /**
     * Sets how it is asked whether a company may use the builder's tool, which is asked again for the companies working
     * supplied jobs here every {@value #TOOL_SECONDS} seconds.
     *
     * @param toolPerk answers whether a company owns the perk and may use it now, by its id
     */
    public void tools(IntFunction<CompletableFuture<Boolean>> toolPerk) {
        this.toolPerk = toolPerk;
        generation.incrementAndGet();
        tools.clear();
        toolsAsked = 0;
    }

    /**
     * Checks the held magic blocks every {@value #SWEEP_TICKS} ticks.
     */
    public void schedule() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, SWEEP_TICKS, SWEEP_TICKS);
    }

    /**
     * Reads the stock of the supplied jobs worked on here, forgets the others, removes the job chests of jobs no longer
     * worked on and takes away the magic blocks their holders may no longer hold.
     */
    void sweep() {
        Map<Integer, JobAccess.Active> working = working();
        swept = Map.copyOf(working);
        askTools(working);
        working.keySet().forEach(stocks::load);
        stocks.retain(working::containsKey);
        for (var job : List.copyOf(chests.keySet())) {
            if (working.containsKey(job)) continue;
            var placed = chests.remove(job);
            if (placed == null) continue;
            for (var block : placed) {
                if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                        && items.chest(block).isPresent()) {
                    block.setType(Material.AIR, false);
                }
            }
        }
        for (var player : plugin.getServer().getOnlinePlayers()) {
            var inventory = player.getInventory();
            for (var slot = 0; slot < inventory.getSize(); slot++) {
                var magic = items.magic(inventory.getItem(slot));
                if (magic.isEmpty()) continue;
                var entry = working.get(magic.get().job());
                if (entry == null || !entry.members().contains(player.getUniqueId()) || !near(player, entry)) {
                    inventory.setItem(slot, null);
                }
            }
        }
    }

    private void askTools(Map<Integer, JobAccess.Active> working) {
        var now = System.currentTimeMillis();
        if (now - toolsAsked < TOOL_SECONDS * 1000L) return;
        toolsAsked = now;
        Set<Integer> companies = new HashSet<>();
        for (var entry : working.values()) {
            var company = entry.job().company();
            if (company != null) companies.add(company);
        }
        tools.keySet().retainAll(companies);
        companies.forEach(this::ask);
    }

    private void ask(int company) {
        if (!asking.add(company)) return;
        var asked = generation.get();
        toolPerk.apply(company).whenComplete((usable, err) -> {
            asking.remove(company);
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Could not read the perks of company " + company, err);
            }
            if (asked == generation.get() && works(company))
                tools.put(company, err == null && Boolean.TRUE.equals(usable));
        });
    }

    private boolean works(int company) {
        for (var entry : swept.values()) {
            if (Integer.valueOf(company).equals(entry.job().company())) return true;
        }
        return false;
    }

    private Map<Integer, JobAccess.Active> working() {
        Map<Integer, JobAccess.Active> working = new LinkedHashMap<>();
        for (var entry : access.active()) {
            if (entry.job().supplied() && entry.job().state() == JobState.TAKEN)
                working.put(entry.job().id(), entry);
        }
        return working;
    }

    private static boolean near(Player player, JobAccess.Active entry) {
        var area = entry.job().area();
        var at = player.getLocation();
        if (!at.getWorld().getName().equals(area.world())) return false;
        return at.getBlockX() >= area.minX() - MARGIN
                && at.getBlockX() <= area.maxX() + MARGIN
                && at.getBlockY() >= area.minY() - MARGIN
                && at.getBlockY() <= area.maxY() + MARGIN
                && at.getBlockZ() >= area.minZ() - MARGIN
                && at.getBlockZ() <= area.maxZ() + MARGIN;
    }

    /**
     * Places a block from a magic block or a job chest from its item, and opens the picker of a job chest.
     *
     * @param event the use
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        var player = event.getPlayer();
        var denied = event.useItemInHand() == Event.Result.DENY;
        var held = event.getItem();
        var magic = items.magic(held);
        var chestItem = items.chest(held);
        var tool = items.tool(held);
        if (magic.isPresent() || chestItem.isPresent() || tool.isPresent()) {
            event.setUseItemInHand(Event.Result.DENY);
        }
        var clicked = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null) return;
        var chest = items.chest(clicked);
        if (chest.isPresent()) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            if (event.getHand() == EquipmentSlot.HAND) useChest(player, clicked, chest.getAsInt());
            return;
        }
        if (magic.isEmpty() && chestItem.isEmpty() && tool.isEmpty()) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        if (denied) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND
                && ours(player.getInventory().getItemInMainHand())) return;
        var target = clicked.getType().isAir() || clicked.isReplaceable()
                ? clicked
                : clicked.getRelative(event.getBlockFace());
        if (tool.isPresent()) {
            build(player, target, tool.getAsInt(), event.getHand());
        } else if (chestItem.isPresent()) {
            placeChest(player, target, chestItem.getAsInt(), event.getHand());
        } else if (magic.get().type() == Material.WATER_BUCKET || magic.get().type() == Material.LAVA_BUCKET) {
            pour(player, target, magic.get());
        } else {
            place(player, target, magic.get(), event.getHand());
        }
    }

    /**
     * Places what the blueprint asks for at a position with the builder's tool of a company: the block from the stock,
     * or a fluid source where the schematic has one.
     *
     * @param player  the member
     * @param block   where the block goes
     * @param company id of the company of the tool
     * @param hand    the hand holding the tool, told to other plugins
     */
    void build(Player player, Block block, int company, @Nullable EquipmentSlot hand) {
        var entry = access.supplied(player, block);
        if (entry.isEmpty()) {
            messages.sendErrorActionBar(player, "company.job.tool.outside");
            return;
        }
        var job = entry.get().job();
        var usable = Integer.valueOf(company).equals(job.company()) ? tools.get(company) : Boolean.FALSE;
        if (usable == null) {
            ask(company);
            messages.sendErrorActionBar(player, "company.job.tool.checking");
            return;
        }
        if (!usable) {
            messages.sendErrorActionBar(player, "company.job.tool.noPerk");
            return;
        }
        var blueprint = blueprints.known(job.id());
        if (blueprint.isEmpty()) {
            messages.sendErrorActionBar(player, "company.job.magic.loading", Values.number("id", job.id()));
            return;
        }
        var want = blueprint.get().want(job.area(), block.getX(), block.getY(), block.getZ());
        if (want == null || want.type().isAir()) {
            var fluid = blueprint.get().fluid(job.area(), block.getX(), block.getY(), block.getZ());
            if (fluid == null) {
                messages.sendErrorActionBar(player, "company.job.tool.nothing");
                return;
            }
            var bucket = fluid == Material.WATER ? Material.WATER_BUCKET : Material.LAVA_BUCKET;
            pour(player, block, new MagicItems.Magic(job.id(), bucket));
            return;
        }
        var takes = BlueprintItems.take(want);
        if (takes.isEmpty()) {
            messages.sendErrorActionBar(player, "company.job.tool.nothing");
            return;
        }
        place(player, block, new MagicItems.Magic(job.id(), takes.getFirst().item()), hand);
    }

    private void useChest(Player player, Block block, int job) {
        if (!access.loaded()) return;
        var entry = working().get(job);
        if (entry == null) {
            block.setType(Material.AIR, false);
            messages.sendErrorActionBar(player, "company.job.magic.chestGone");
            return;
        }
        if (!entry.members().contains(player.getUniqueId())) {
            messages.sendErrorActionBar(player, "company.job.magic.chestNotYours");
            return;
        }
        chests.computeIfAbsent(job, id -> ConcurrentHashMap.newKeySet()).add(block);
        open(player, job, 0);
    }

    /**
     * Places a job chest from its item.
     *
     * @param player the member
     * @param block  where it goes
     * @param job    id of the job of the item
     * @param hand   the hand holding the item, which loses it
     */
    void placeChest(Player player, Block block, int job, @Nullable EquipmentSlot hand) {
        var entry = access.supplied(player, block).filter(found -> found.job().id() == job);
        if (entry.isEmpty()) {
            messages.sendErrorActionBar(player, "company.job.magic.chestOutside", Values.number("id", job));
            return;
        }
        var want = want(entry.get(), block);
        if ((want != null && !want.type().isAir()) || access.counted(job, JobAccess.key(block))) {
            messages.sendErrorActionBar(player, "company.job.magic.chestSpot");
            return;
        }
        if (!free(block) || standing(block)) {
            messages.sendErrorActionBar(player, "company.job.magic.occupied");
            return;
        }
        block.setType(MagicItems.CHEST, false);
        items.mark(block, job);
        chests.computeIfAbsent(job, id -> ConcurrentHashMap.newKeySet()).add(block);
        if (hand != null) {
            var item = player.getInventory().getItem(hand);
            if (items.chest(item).isPresent()) item.setAmount(item.getAmount() - 1);
        }
    }

    /**
     * Places the block the blueprint asks for from a magic block.
     *
     * @param player the member
     * @param block  where the block goes
     * @param magic  the magic block
     * @param hand   the hand holding it, told to other plugins
     */
    void place(Player player, Block block, MagicItems.Magic magic, @Nullable EquipmentSlot hand) {
        var entry = access.supplied(player, block).filter(found -> found.job().id() == magic.job());
        if (entry.isEmpty()) {
            messages.sendErrorActionBar(player, "company.job.magic.outside", Values.number("id", magic.job()));
            return;
        }
        var blueprint = blueprints.known(magic.job());
        if (blueprint.isEmpty()) {
            messages.sendErrorActionBar(player, "company.job.magic.loading", Values.number("id", magic.job()));
            return;
        }
        var job = entry.get().job();
        var want = blueprint.get().want(job.area(), block.getX(), block.getY(), block.getZ());
        var takes = want == null ? List.<BlueprintItems.Take>of() : BlueprintItems.take(want);
        if (want == null || takes.isEmpty() || takes.getFirst().item() != magic.type()) {
            refuse(player, want, magic.type());
            return;
        }
        if (matches(want, block.getBlockData())) {
            messages.sendErrorActionBar(player, "company.job.magic.done");
            return;
        }
        List<Block> spots = new ArrayList<>();
        spots.add(block);
        var partner = partner(blueprint.get(), job.area(), block, want);
        if (partner != null) spots.add(partner);
        for (var spot : spots) {
            if (!free(spot) || standing(spot)) {
                messages.sendErrorActionBar(player, "company.job.magic.occupied");
                return;
            }
        }
        Map<String, Integer> needed = new LinkedHashMap<>();
        takes.forEach(take -> needed.merge(take.item().name(), take.amount(), Integer::sum));
        if (!stocks.take(job.id(), needed)) {
            empty(player, job.id(), magic.type());
            return;
        }
        var replaced = block.getState();
        Map<Block, BlockData> before = new LinkedHashMap<>();
        for (var spot : spots) {
            var wanted = blueprint.get().want(job.area(), spot.getX(), spot.getY(), spot.getZ());
            if (wanted == null) continue;
            before.put(spot, spot.getBlockData());
            spot.setBlockData(data(wanted), false);
        }
        var placing = new Placing(
                block, replaced, player, new ItemStack(magic.type()), hand == null ? EquipmentSlot.HAND : hand);
        plugin.getServer().getPluginManager().callEvent(placing);
        if (placing.isCancelled()) {
            before.forEach((spot, data) -> spot.setBlockData(data, false));
            stocks.give(job.id(), needed);
            return;
        }
        for (var spot : before.keySet()) {
            access.placedFromStock(entry.get(), spot, player.getUniqueId());
            ghosts.refresh(spot);
        }
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1, 1);
    }

    private static boolean free(Block block) {
        return block.getType().isAir() || block.isReplaceable();
    }

    private static boolean standing(Block block) {
        var box = BoundingBox.of(block).expand(0, 1, 0, 0, 0, 0);
        return !block.getWorld()
                .getNearbyEntities(box, entity -> entity instanceof LivingEntity)
                .isEmpty();
    }

    /**
     * The placement of a block from a magic block, told to other plugins such as protections and loggers after the
     * block is set; cancelling it takes the block back and puts its items back into the stock. The job's own rules
     * leave it alone.
     */
    public static final class Placing extends BlockPlaceEvent {
        /**
         * @param block    the placed block
         * @param replaced what stood there before
         * @param player   the member placing it
         * @param item     an item of the type placed
         * @param hand     the hand holding the magic block
         */
        public Placing(Block block, BlockState replaced, Player player, ItemStack item, EquipmentSlot hand) {
            super(block, replaced, block.getRelative(BlockFace.DOWN), item, player, true, hand);
        }
    }

    private void refuse(Player player, Blueprint.@Nullable Want want, Material held) {
        if (want != null && !want.type().isAir()) {
            messages.sendErrorActionBar(
                    player,
                    "company.job.error.blueprintBlock",
                    Values.text("block", PlainText.keyName(want.type().getKey())));
            return;
        }
        messages.sendErrorActionBar(
                player, "company.job.magic.noSpot", Values.text("block", PlainText.keyName(held.getKey())));
    }

    private void empty(Player player, int job, Material type) {
        if (stocks.of(job).isEmpty()) {
            messages.sendErrorActionBar(player, "company.job.magic.loading", Values.number("id", job));
            return;
        }
        var inventory = player.getInventory();
        for (var slot = 0; slot < inventory.getSize(); slot++) {
            var magic = items.magic(inventory.getItem(slot));
            if (magic.isPresent() && magic.get().job() == job && magic.get().type() == type) {
                inventory.setItem(slot, null);
            }
        }
        messages.sendErrorActionBar(
                player,
                "company.job.magic.empty",
                Values.number("id", job),
                Values.text("block", PlainText.keyName(type.getKey())));
    }

    /**
     * @param blueprint the blueprint
     * @param area      the job's area
     * @param block     a position of a block of two halves
     * @param want      what the blueprint asks for there
     * @return the position of its other half, as its block data says, when the blueprint asks for the same type there;
     *     null for a block of one part
     */
    static @Nullable Block partner(Blueprint blueprint, JobArea area, Block block, Blueprint.Want want) {
        var data = data(want);
        Block other;
        if (data instanceof Bed bed) {
            other = block.getRelative(
                    bed.getPart() == Bed.Part.FOOT
                            ? bed.getFacing()
                            : bed.getFacing().getOppositeFace());
        } else if (data instanceof Bisected bisected && !(data instanceof Stairs) && !(data instanceof TrapDoor)) {
            other = block.getRelative(bisected.getHalf() == Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN);
        } else {
            return null;
        }
        var wanted = blueprint.want(area, other.getX(), other.getY(), other.getZ());
        return wanted != null && wanted.type() == want.type() ? other : null;
    }

    private static BlockData data(Blueprint.Want want) {
        try {
            return Bukkit.createBlockData(want.data());
        } catch (IllegalArgumentException e) {
            return want.type().createBlockData();
        }
    }

    private static boolean matches(Blueprint.Want want, BlockData data) {
        if (data.getMaterial() != want.type()) return false;
        return want.fixed().isEmpty() || want.matches(data.getMaterial(), data.getAsString());
    }

    private Blueprint.@Nullable Want want(JobAccess.Active entry, Block block) {
        var area = entry.job().area();
        return blueprints
                .known(entry.job().id())
                .map(blueprint -> blueprint.want(area, block.getX(), block.getY(), block.getZ()))
                .orElse(null);
    }

    /**
     * Pours a fluid source from magic water or lava.
     *
     * @param player the member
     * @param block  where the source goes
     * @param magic  the magic bucket
     */
    void pour(Player player, Block block, MagicItems.Magic magic) {
        var entry = access.supplied(player, block).filter(found -> found.job().id() == magic.job());
        var blueprint = blueprints.known(magic.job());
        if (entry.isEmpty() || blueprint.isEmpty()) {
            messages.sendErrorActionBar(player, "company.job.magic.outside", Values.number("id", magic.job()));
            return;
        }
        var job = entry.get().job();
        var fluid = magic.type() == Material.WATER_BUCKET ? Material.WATER : Material.LAVA;
        if (blueprint.get().fluid(job.area(), block.getX(), block.getY(), block.getZ()) != fluid) {
            refuse(player, null, magic.type());
            return;
        }
        if (block.getType() == fluid) {
            messages.sendErrorActionBar(player, "company.job.magic.done");
            return;
        }
        if (!free(block)) {
            messages.sendErrorActionBar(player, "company.job.magic.occupied");
            return;
        }
        var poured = fluid == Material.WATER
                ? stocks.of(job.id())
                        .map(stock -> stock.getOrDefault(Material.WATER_BUCKET.name(), 0) >= WATER_BUCKETS)
                        .orElse(false)
                : stocks.take(job.id(), Map.of(Material.LAVA_BUCKET.name(), 1));
        if (!poured) {
            empty(player, job.id(), magic.type());
            return;
        }
        block.setType(fluid);
    }

    /**
     * Keeps members from filling buckets where the schematic has a fluid source, so poured lava stays in the job.
     *
     * @param event the filled bucket
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        var block = event.getBlockClicked();
        var entry = access.supplied(event.getPlayer(), block);
        if (entry.isEmpty()) return;
        var area = entry.get().job().area();
        var poured = blueprints
                .known(entry.get().job().id())
                .map(blueprint -> blueprint.fluid(area, block.getX(), block.getY(), block.getZ()))
                .orElse(null);
        if (poured != null) event.setCancelled(true);
    }

    /**
     * A member's break that gives a block back to the stock.
     *
     * @param entry the job
     * @param items what goes back, as amounts by material name
     * @param spots the positions opened again
     */
    private record TakeBack(JobAccess.Active entry, Map<String, Integer> items, List<Block> spots) {}

    /**
     * Breaks a job chest without a drop, keeps the drop and experience of a block a member gives back to the stock, and
     * keeps a member from breaking in a job whose blueprint is not read yet.
     *
     * @param event the break, as the protections left it
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        var block = event.getBlock();
        var chest = items.chest(block);
        if (chest.isPresent()) {
            event.setDropItems(false);
            var placed = chests.get(chest.getAsInt());
            if (placed != null) placed.remove(block);
            return;
        }
        var entry = access.supplied(event.getPlayer(), block);
        if (entry.isEmpty()) return;
        if (blueprints.known(entry.get().job().id()).isEmpty()) {
            event.setCancelled(true);
            messages.sendErrorActionBar(
                    event.getPlayer(),
                    "company.job.magic.loading",
                    Values.number("id", entry.get().job().id()));
            return;
        }
        if (takeBack(entry.get(), block).isEmpty()) return;
        event.setDropItems(false);
        event.setExpToDrop(0);
    }

    /**
     * Gives a block a member broke back to the stock, once the break went through.
     *
     * @param event the break that happens
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBroken(BlockBreakEvent event) {
        access.supplied(event.getPlayer(), event.getBlock())
                .flatMap(entry -> takeBack(entry, event.getBlock()))
                .ifPresent(this::giveBack);
    }

    /**
     * @param entry a supplied job the breaker works on
     * @param block a block of its area
     * @return what breaking it gives back to the stock: the items of the block the blueprint asks for there and of its
     *     other half, when the block is that type or was placed from the stock; empty for any other block
     */
    private Optional<TakeBack> takeBack(JobAccess.Active entry, Block block) {
        var job = entry.job();
        var blueprint = blueprints.known(job.id());
        if (blueprint.isEmpty()) return Optional.empty();
        var want = blueprint.get().want(job.area(), block.getX(), block.getY(), block.getZ());
        if (want == null || want.type().isAir()) return Optional.empty();
        var counted = access.counted(job.id(), JobAccess.key(block));
        if (block.getType().isAir() || (!counted && block.getType() != want.type())) return Optional.empty();
        Map<String, Integer> back = new TreeMap<>();
        List<Block> spots = new ArrayList<>();
        spots.add(block);
        BlueprintItems.take(want).forEach(take -> back.merge(take.item().name(), take.amount(), Integer::sum));
        var partner = partner(blueprint.get(), job.area(), block, want);
        if (partner != null && (partner.getType() == want.type() || access.counted(job.id(), JobAccess.key(partner)))) {
            spots.add(partner);
            var other = blueprint.get().want(job.area(), partner.getX(), partner.getY(), partner.getZ());
            if (other != null) {
                BlueprintItems.take(other)
                        .forEach(take -> back.merge(take.item().name(), take.amount(), Integer::sum));
            }
        }
        return Optional.of(new TakeBack(entry, back, spots));
    }

    private void giveBack(TakeBack back) {
        for (var spot : back.spots()) access.takenBack(back.entry().job(), spot);
        stocks.give(back.entry().job().id(), back.items());
    }

    /**
     * Gives a block placed from the stock that pops off or is washed away back to the stock, without a drop.
     *
     * @param event the block destroyed by the world
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        var block = event.getBlock();
        var entry = counted(block);
        if (entry.isEmpty()) return;
        var job = entry.get().job();
        var want = want(entry.get(), block);
        event.setWillDrop(false);
        access.takenBack(job, block);
        if (want == null || block.getType() != want.type()) return;
        Map<String, Integer> back = new TreeMap<>();
        BlueprintItems.take(want).forEach(take -> back.merge(take.item().name(), take.amount(), Integer::sum));
        stocks.give(job.id(), back);
    }

    /**
     * @param block a block
     * @return the supplied job whose area holds the block, when a block placed from its stock counts there, as the
     *     last sweep found the jobs
     */
    private Optional<JobAccess.Active> counted(Block block) {
        var world = block.getWorld().getName();
        for (var entry : swept.values()) {
            var job = entry.job();
            if (job.area().contains(world, block.getX(), block.getY(), block.getZ())
                    && access.counted(job.id(), JobAccess.key(block))) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * Leaves blocks placed from the stock standing in an explosion.
     *
     * @param event the explosion
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::kept);
    }

    /**
     * Leaves blocks placed from the stock standing in an explosion of a block.
     *
     * @param event the explosion
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::kept);
    }

    private boolean kept(Block block) {
        return items.chest(block).isPresent() || counted(block).isPresent();
    }

    /**
     * Keeps flowing fluids from washing away blocks placed from the stock.
     *
     * @param event the flow
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        var into = event.getToBlock();
        if (!into.isLiquid() && !into.getType().isAir() && counted(into).isPresent()) event.setCancelled(true);
    }

    /**
     * Keeps leaves placed from the stock from decaying.
     *
     * @param event the decay
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (counted(event.getBlock()).isPresent()) event.setCancelled(true);
    }

    /**
     * Keeps pistons from pushing blocks placed from the stock.
     *
     * @param event the push
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPush(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> counted(block).isPresent())) event.setCancelled(true);
    }

    /**
     * Keeps pistons from pulling blocks placed from the stock.
     *
     * @param event the pull
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPull(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> counted(block).isPresent())) event.setCancelled(true);
    }

    /**
     * Keeps blocks placed from the stock from falling or being taken by creatures.
     *
     * @param event a block changed by an entity
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player) && counted(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /**
     * Keeps hoppers from putting items into a job chest, which keeps none.
     *
     * @param event the move
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (event.getDestination().getHolder(false) instanceof Barrel barrel
                && items.chest(barrel.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /**
     * The picker of a job chest.
     */
    private static final class Picker implements InventoryHolder {
        private final int job;
        private final int page;
        private final List<Material> types;
        private Inventory inventory;

        private Picker(int job, int page, List<Material> types) {
            this.job = job;
            this.page = page;
            this.types = types;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    /**
     * @param job id of a supplied job
     * @return the item types its blueprint takes, by name
     */
    List<Material> types(int job) {
        return blueprints
                .known(job)
                .map(blueprint -> {
                    var needs = BlueprintItems.of(blueprint);
                    Set<Material> types = new HashSet<>(needs.items().keySet());
                    types.addAll(needs.fluids().keySet());
                    return types.stream()
                            .filter(Material::isItem)
                            .sorted(Comparator.comparing(Material::name))
                            .toList();
                })
                .orElse(List.of());
    }

    private void open(Player player, int job, int page) {
        var types = types(job);
        var pages = Math.max(1, (types.size() + PAGE - 1) / PAGE);
        var shown = Math.clamp(page, 0, pages - 1);
        var components = new MenuComponents(messages, player);
        var holder = new Picker(job, shown, types);
        var inventory = plugin.getServer()
                .createInventory(holder, SIZE, components.locale("company.job.magic.picker", Values.number("id", job)));
        holder.inventory = inventory;
        var stock = stocks.of(job).orElse(Map.of());
        for (var slot = 0; slot < PAGE && shown * PAGE + slot < types.size(); slot++) {
            var type = types.get(shown * PAGE + slot);
            var left = stock.getOrDefault(type.name(), 0);
            var item = new ItemStack(left > 0 ? type : Material.GRAY_STAINED_GLASS_PANE);
            item.editMeta(meta -> {
                meta.displayName(Component.translatable(type.translationKey()));
                meta.lore(List.of(
                        left > 0
                                ? components.locale("company.job.magic.left", Values.number("amount", left))
                                : components.locale("company.job.magic.usedUp")));
            });
            inventory.setItem(slot, item);
        }
        if (shown > 0) inventory.setItem(PREVIOUS, button(components, "company.job.magic.previous"));
        if (shown < pages - 1) inventory.setItem(NEXT, button(components, "company.job.magic.next"));
        player.openInventory(inventory);
    }

    private static ItemStack button(MenuComponents components, String key) {
        var item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> meta.displayName(components.locale(key)));
        return item;
    }

    /**
     * Hands out a magic block from the picker, turns its pages, and keeps magic blocks in their holder's inventory.
     *
     * @param event the click
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getHolder(false) instanceof Picker picker) {
            event.setCancelled(true);
            var raw = event.getRawSlot();
            if (raw == PREVIOUS || raw == NEXT) {
                var page = picker.page + (raw == NEXT ? 1 : -1);
                plugin.getServer().getScheduler().runTask(plugin, () -> open(player, picker.job, page));
                return;
            }
            var index = picker.page * PAGE + raw;
            if (raw < 0 || raw >= PAGE || index >= picker.types.size()) return;
            pick(player, picker.job, picker.types.get(index));
            return;
        }
        if (moves(event, player)) event.setCancelled(true);
    }

    /**
     * @param event  a click outside the picker
     * @param player the player clicking
     * @return whether it would move a magic block or job chest item into another inventory
     */
    boolean moves(InventoryClickEvent event, Player player) {
        var top = event.getView().getTopInventory();
        var own = top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.CREATIVE;
        var ours = ours(event.getCurrentItem()) || ours(event.getCursor());
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            ours |= ours(player.getInventory().getItem(event.getHotbarButton()));
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ours |= ours(player.getInventory().getItemInOffHand());
        }
        if (!ours) return false;
        if (!own) return true;
        return event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize();
    }

    private boolean ours(@Nullable ItemStack item) {
        return items.isMagic(item)
                || items.chest(item).isPresent()
                || items.tool(item).isPresent();
    }

    /**
     * Gives a member a magic block of a type, in place of the magic blocks of the job they hold.
     *
     * @param player the member
     * @param job    id of the job
     * @param type   the type
     */
    void pick(Player player, int job, Material type) {
        var entry = working().get(job);
        if (entry == null || !entry.members().contains(player.getUniqueId()) || !type.isItem()) return;
        if (stocks.of(job).map(stock -> stock.getOrDefault(type.name(), 0)).orElse(0) <= 0) {
            empty(player, job, type);
            return;
        }
        var inventory = player.getInventory();
        for (var slot = 0; slot < inventory.getSize(); slot++) {
            var magic = items.magic(inventory.getItem(slot));
            if (magic.isPresent() && magic.get().job() == job) inventory.setItem(slot, null);
        }
        var components = new MenuComponents(messages, player);
        var item = items.magic(
                job,
                type,
                Component.translatable(type.translationKey()),
                List.of(components.locale("company.job.magic.lore", Values.number("id", job))));
        if (inventory.getItemInMainHand().getType().isAir()) {
            inventory.setItemInMainHand(item);
        } else if (!inventory.addItem(item).isEmpty()) {
            messages.sendErrorActionBar(player, "company.job.magic.full");
            return;
        }
        player.closeInventory();
    }

    /**
     * Keeps magic blocks from being dragged into another inventory and picker items from being moved.
     *
     * @param event the drag
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        var top = event.getView().getTopInventory();
        if (top.getHolder(false) instanceof Picker) {
            event.setCancelled(true);
            return;
        }
        if (!ours(event.getOldCursor())) return;
        if (event.getRawSlots().stream().anyMatch(raw -> raw < top.getSize())) event.setCancelled(true);
    }

    /**
     * Keeps magic blocks and job chest items from being dropped, and lets a dropped builder's tool go, as it can be
     * had again.
     *
     * @param event the drop
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        var item = event.getItemDrop().getItemStack();
        if (items.tool(item).isPresent()) {
            event.getItemDrop().remove();
            return;
        }
        if (ours(item)) event.setCancelled(true);
    }

    /**
     * Takes magic blocks away from a player who died.
     *
     * @param event the death
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::ours);
        event.getItemsToKeep().removeIf(this::ours);
    }

    /**
     * Takes magic blocks away from a player who quits.
     *
     * @param event the quit
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var inventory = event.getPlayer().getInventory();
        for (var slot = 0; slot < inventory.getSize(); slot++) {
            if (items.isMagic(inventory.getItem(slot))) inventory.setItem(slot, null);
        }
    }
}
