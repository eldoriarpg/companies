/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.data.repository.AJobData;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobMark;
import de.eldoria.companies.jobs.hooks.JobHooks;
import de.eldoria.companies.util.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the access to the areas of jobs right on the server whose worlds hold them, and counts the blocks each worker
 * removes from an area.
 * <p>
 * Every check reads the jobs of this server that wait, are taken or are still noted as granted, and asks the land hooks
 * about all of them in one round on the main thread: whether the land is still the owner's and, for taken jobs, which
 * players they would trust. A job whose land changed hands, such as a plot that was sold, is handed to {@code
 * landLost}. The players a hook is about to add are stored before it adds them, so a crash never leaves access nobody
 * takes back. A second round on the main thread first takes the access of every job that is not taken any more back,
 * then gives each taken job exactly the current members of its company, so members who joined or left are added or
 * removed and a job that ended never takes away what another job of the same land still needs. A hook that can not take
 * access back yet, such as while the world is not loaded, is asked again at the next check. Each round on the main
 * thread waits at most {@value #MAIN_THREAD_SECONDS} seconds. Checks run every {@value #CHECK_SECONDS} seconds and soon
 * after a change on this server.
 * <p>
 * A block a member of the working company breaks inside a taken job's area counts as their work, unless it was placed
 * there while the job ran. In a terraform job's area only breaking what the target asks to go counts: blocks above the
 * target height and top layer blocks that are not its surface block. Placing a block that fills a position as the
 * target asks counts too, once for each position, and never where a member broke a block that already filled it, so
 * taking out a solid block and putting it back earns nothing. Counts are written every {@value #FLUSH_SECONDS} seconds,
 * before a job is finished and when the plugin stops, and only while the company they were counted for still has the
 * job. The placed and filled positions are written with them, for the company they were counted for, and read back when
 * a job becomes active on this server again, so a restart never lets a position count twice; they are dropped once the
 * job is neither taken nor waiting for approval nor disputed by that company, and a company that takes a job over
 * starts with none. A failed write is tried again with the next one. A dig job nobody worked on yet is handed to {@code
 * untouched}, so the blocks already cleared when it was posted can be noted; a taken one gets its access only once that
 * measuring ended. Only the working company may place blocks or empty buckets in a taken job's area, so nobody fills it
 * up while it is worked on. In a build job's area it may place only the block the blueprint asks for at a position, and
 * the blocks of the build whitelist anywhere to build up; a block of the right type placed turned another way is turned
 * as the blueprint asks on the next tick. In the area of a build job whose owner supplies the materials it places only
 * magic blocks and the job chest, by their own rules, and the blocks of the build whitelist; a block placed from the
 * stock counts as placed until a member breaks it again, and anyone else may break it without undoing that. A land
 * hook whose access reaches beyond the job's area, such as the trust on
 * a whole plot, is held to the area: players who only have access through a job may not break, place, use buckets, use
 * blocks or touch entities outside the areas of the jobs they work on, though they may still open doors, use the item
 * in their hand and fight monsters. A disputed job's area is frozen until an admin settles it: nobody has access
 * through it and nobody, its owner included, may place blocks or pour into it. Nobody may pour lava, place TNT or set
 * fire in a taken job's area. Players with {@code companies.admin.jobs.bypass} are held by none of this.
 */
public final class JobAccess implements Listener {
    /**
     * How often access is checked, in seconds.
     */
    public static final int CHECK_SECONDS = 30;

    /**
     * How often counted work is written, in seconds.
     */
    public static final int FLUSH_SECONDS = 10;

    /**
     * How long a check waits for the main thread, in seconds.
     */
    public static final int MAIN_THREAD_SECONDS = 10;

    private final AJobData jobs;
    private final IntFunction<Set<UUID>> members;
    private final JobHooks hooks;
    private final UUID node;
    private final Executor mainThread;
    private final Consumer<Job> landLost;
    private final Function<Job, CompletableFuture<?>> untouched;
    private final Harvests harvests;
    private final Blueprints blueprints;
    private final Logger logger;
    private final Map<Work, Integer> pending = new ConcurrentHashMap<>();
    private final Map<Work, Map<String, Integer>> harvested = new ConcurrentHashMap<>();
    private final Map<Integer, Set<Long>> placed = new ConcurrentHashMap<>();
    private final Map<Integer, Set<Long>> filled = new ConcurrentHashMap<>();
    private final Map<Integer, Set<Long>> supplied = new ConcurrentHashMap<>();
    private final Map<JobMark, Boolean> marks = new ConcurrentHashMap<>();
    private final Set<Integer> cleared = ConcurrentHashMap.newKeySet();
    private final Set<Integer> measured = ConcurrentHashMap.newKeySet();
    private final Set<Integer> tried = ConcurrentHashMap.newKeySet();
    private final Map<Integer, CompletableFuture<?>> measuring = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> owners = new ConcurrentHashMap<>();
    private final Object writing = new Object();
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile List<Active> active = List.of();
    private volatile ScheduledExecutorService executor;
    private volatile Supplier<Set<Material>> whitelist = () -> Set.of(Material.SCAFFOLDING);
    private volatile BiConsumer<Player, Blueprint.@Nullable Want> refused = (player, want) -> {};
    private volatile Consumer<Player> onlyMagic = player -> {};
    private volatile boolean loaded;

    /**
     * @param jobs       job repository
     * @param members    the members of a company, by its id; empty for a company that is gone
     * @param hooks      gives and takes access through the land plugins
     * @param node       this server
     * @param mainThread runs the hooks on the main thread
     * @param landLost   closes a job whose land changed hands
     * @param untouched  notes the blocks already cleared in the area of a dig job nobody worked on yet, completing when
     *                   it is done
     * @param harvests   stores and pays what a member harvested
     * @param blueprints the blueprints of build jobs, read for taken ones at each check
     * @param logger     reports failures
     */
    public JobAccess(
            AJobData jobs,
            IntFunction<Set<UUID>> members,
            JobHooks hooks,
            UUID node,
            Executor mainThread,
            Consumer<Job> landLost,
            Function<Job, CompletableFuture<?>> untouched,
            Harvests harvests,
            Blueprints blueprints,
            Logger logger) {
        this.jobs = jobs;
        this.members = members;
        this.hooks = hooks;
        this.node = node;
        this.mainThread = mainThread;
        this.landLost = landLost;
        this.untouched = untouched;
        this.harvests = harvests;
        this.blueprints = blueprints;
        this.logger = logger;
    }

    /**
     * Sets how the working company of a build job is held to its blueprint.
     *
     * @param whitelist the blocks it may place anywhere in the area to build up
     * @param refused   tells a player who placed a block the blueprint does not ask for there what it asks for, null
     *                  for nothing
     */
    public void building(Supplier<Set<Material>> whitelist, BiConsumer<Player, Blueprint.@Nullable Want> refused) {
        this.whitelist = whitelist;
        this.refused = refused;
    }

    /**
     * Sets how a supplied build job is held to its stock: in its area the working company builds with magic blocks,
     * which place from the stock by their own rules, and places only the blocks of the build whitelist itself.
     *
     * @param onlyMagic tells a player who placed or poured anything else that only magic blocks build there
     */
    public void supplying(Consumer<Player> onlyMagic) {
        this.onlyMagic = onlyMagic;
    }

    /**
     * Checks the access now and then regularly, and writes counted work regularly.
     *
     * @param executor runs the checks
     */
    public void schedule(ScheduledExecutorService executor) {
        this.executor = executor;
        executor.scheduleWithFixedDelay(this::check, 0, CHECK_SECONDS, TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(this::flush, FLUSH_SECONDS, FLUSH_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Stops the checks and writes the counted work.
     */
    public void stop() {
        executor = null;
        flush();
    }

    /**
     * Asks for a check soon, after a job of this server changed. Several asks before it runs make one check.
     */
    public void checkSoon() {
        var current = executor;
        if (current == null || current.isShutdown() || !queued.compareAndSet(false, true)) return;
        try {
            current.execute(this::check);
        } catch (RuntimeException e) {
            queued.set(false);
        }
    }

    /**
     * Gives the taken jobs of this server's areas exactly their company's members, takes the access of every other
     * granted job back, and closes jobs whose land changed hands.
     */
    public synchronized void check() {
        queued.set(false);
        try {
            List<Plan> plans = new ArrayList<>();
            Set<Integer> building = new HashSet<>();
            Set<Integer> digging = new HashSet<>();
            Set<Integer> held = new HashSet<>();
            Set<Integer> waiting = new HashSet<>();
            for (var job : jobs.access(node)) {
                if (job.kind() == JobKind.BUILD && !job.state().closed()) building.add(job.id());
                if (job.state() == JobState.TAKEN || job.state() == JobState.DONE || job.state() == JobState.DISPUTED) {
                    held.add(job.id());
                }
                if (fresh(job)) {
                    digging.add(job.id());
                    if (measure(job)) waiting.add(job.id());
                }
                if (job.kind() == JobKind.BUILD && job.state() == JobState.TAKEN) blueprints.of(job.id());
                var company = job.company();
                var taken = job.state() == JobState.TAKEN && company != null;
                plans.add(new Plan(
                        job,
                        taken ? members.apply(company) : Set.of(),
                        taken || job.granted() ? jobs.grants(job.id()) : Set.of()));
            }
            var asked = onMain(() -> ask(plans), null);
            if (asked == null) return;
            List<Plan> granting = new ArrayList<>();
            List<Plan> revoking = new ArrayList<>();
            List<Job> frozen = new ArrayList<>();
            for (var plan : plans) {
                var answer = asked.get(plan.job().id());
                var running = !plan.job().state().closed();
                if (running && !answer.owned()) {
                    lost(plan.job());
                    running = false;
                }
                if (waiting.contains(plan.job().id())) continue;
                if (running
                        && plan.job().state() == JobState.TAKEN
                        && plan.job().company() != null) {
                    var planned = answer.planned();
                    if (!planned.equals(plan.before())
                            && !jobs.grants(plan.job().id(), planned)) continue;
                    granting.add(new Plan(plan.job(), plan.members(), planned));
                    continue;
                }
                if (running && plan.job().state() == JobState.DISPUTED) frozen.add(plan.job());
                if (plan.job().granted()) revoking.add(plan);
            }
            var done = onMain(() -> apply(revoking, granting), null);
            if (done == null) return;
            for (var plan : revoking) {
                if (!done.revoked().contains(plan.job().id())) continue;
                jobs.grants(plan.job().id(), Set.of());
                jobs.granted(plan.job().id(), false);
            }
            List<Active> now = new ArrayList<>();
            for (var plan : granting) {
                var given = done.given().getOrDefault(plan.job().id(), plan.before());
                if (!given.equals(plan.before())) jobs.grants(plan.job().id(), given);
                if (!plan.job().granted()) jobs.granted(plan.job().id(), true);
                now.add(new Active(plan.job(), plan.members(), given));
            }
            for (var job : frozen) now.add(new Active(job, Set.of(), Set.of()));
            for (var entry : now) load(entry.job());
            active = List.copyOf(now);
            loaded = true;
            blueprints.prune(building::contains);
            measured.retainAll(digging);
            tried.retainAll(digging);
            filled.keySet().retainAll(held);
            placed.keySet().retainAll(held);
            supplied.keySet().retainAll(held);
            owners.keySet().retainAll(held);
            marks.keySet().removeIf(mark -> !held.contains(mark.job()));
            jobs.pruneMarks();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not check the access to the areas of jobs", e);
        }
    }

    /**
     * @param job a job of this server
     * @return whether it is a dig job nobody may work on yet: open, or taken before its access was given
     */
    private static boolean fresh(Job job) {
        return job.kind() == JobKind.DIG
                && (job.state() == JobState.OPEN || job.state() == JobState.TAKEN && !job.granted());
    }

    /**
     * Measures a fresh dig job once its blocks are not noted yet. An open job is measured at each check until it is; a
     * taken one is measured once and waits for its access while that runs.
     *
     * @param job a fresh dig job
     * @return whether the job waits for its measuring before it gets its access
     */
    private boolean measure(Job job) {
        var id = job.id();
        if (measured.contains(id)) return false;
        var running = measuring.get(id);
        if (running != null) return job.state() == JobState.TAKEN;
        if (jobs.airAtPost(id).isPresent()) {
            measured.add(id);
            return false;
        }
        if (job.state() == JobState.TAKEN && !tried.add(id)) return false;
        try {
            var started = untouched.apply(job);
            measuring.put(id, started);
            started.whenComplete((result, err) -> {
                measuring.remove(id);
                if (err != null) logger.log(Level.WARNING, "Could not measure the area of job " + id, err);
                checkSoon();
            });
            return job.state() == JobState.TAKEN && measuring.containsKey(id);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not measure the area of job " + id, e);
            return false;
        }
    }

    /**
     * Reads the remembered positions of an active job when they are not read yet, or were read for a company that no
     * longer has the job.
     *
     * @param job an active job
     */
    private void load(Job job) {
        var company = job.company();
        if (company == null || company.equals(owners.get(job.id()))) return;
        var id = job.id();
        marks.keySet().removeIf(mark -> mark.job() == id && mark.company() != company);
        var stored = jobs.marks(id, company);
        placed.put(id, copy(stored.get(JobMark.Kind.PLACED)));
        filled.put(id, copy(stored.get(JobMark.Kind.FILLED)));
        supplied.put(id, copy(stored.get(JobMark.Kind.SUPPLIED)));
        owners.put(id, company);
    }

    private static Set<Long> copy(@Nullable Set<Long> positions) {
        Set<Long> copy = ConcurrentHashMap.newKeySet();
        if (positions != null) copy.addAll(positions);
        return copy;
    }

    private Map<Integer, Answer> ask(List<Plan> plans) {
        Map<Integer, Answer> answers = new HashMap<>();
        for (var plan : plans) {
            var job = plan.job();
            var owned = job.state().closed() || hooks.owns(job);
            var planned = owned && job.state() == JobState.TAKEN
                    ? hooks.planned(job, plan.members(), plan.before())
                    : plan.before();
            answers.put(job.id(), new Answer(owned, planned));
        }
        return answers;
    }

    private Applied apply(List<Plan> revoking, List<Plan> granting) {
        Set<Integer> revoked = new HashSet<>();
        for (var plan : revoking) {
            if (hooks.revoke(plan.job(), plan.before())) revoked.add(plan.job().id());
        }
        Map<Integer, Set<UUID>> given = new HashMap<>();
        for (var plan : granting) {
            given.put(plan.job().id(), hooks.grant(plan.job(), plan.members(), plan.before()));
        }
        return new Applied(revoked, given);
    }

    private void lost(Job job) {
        try {
            landLost.accept(job);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not close job " + job.id() + " whose land changed hands", e);
        }
    }

    /**
     * Writes the counted work to the database.
     */
    public void flush() {
        flush(work -> true);
    }

    /**
     * Writes the counted work of a job to the database, before it is finished.
     *
     * @param job id of the job
     */
    public void flush(int job) {
        flush(work -> work.job() == job);
    }

    private void flush(Predicate<Work> which) {
        writeMarks();
        Map<Crops, Map<UUID, Map<String, Integer>>> crops = new HashMap<>();
        for (var key : List.copyOf(harvested.keySet())) {
            if (!which.test(key)) continue;
            var items = harvested.remove(key);
            if (items == null || items.isEmpty()) continue;
            crops.computeIfAbsent(new Crops(key.job(), key.company()), field -> new LinkedHashMap<>())
                    .put(key.worker(), items);
        }
        crops.forEach((field, byWorker) -> {
            try {
                harvests.harvested(field.job(), field.company(), byWorker);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not store the harvest " + byWorker + " of job " + field.job(), e);
            }
        });
        Map<Work, Integer> taken = new HashMap<>();
        for (var key : List.copyOf(pending.keySet())) {
            if (!which.test(key)) continue;
            var blocks = pending.remove(key);
            if (blocks != null && blocks > 0) taken.put(key, blocks);
        }
        taken.forEach((work, blocks) -> {
            try {
                jobs.work(work.job(), work.company(), work.worker(), blocks);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not count the work on job " + work.job(), e);
            }
        });
    }

    private void writeMarks() {
        synchronized (writing) {
            for (var job : List.copyOf(cleared)) {
                try {
                    jobs.clearMarks(job);
                    cleared.remove(job);
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "Could not forget the positions of job " + job, e);
                }
            }
            Map<JobMark, Boolean> changes = new HashMap<>();
            for (var mark : List.copyOf(marks.keySet())) {
                var change = marks.remove(mark);
                if (change != null) changes.put(mark, change);
            }
            var written = false;
            try {
                written = jobs.marks(changes);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not write the positions of jobs", e);
            }
            if (!written) changes.forEach(marks::putIfAbsent);
        }
    }

    private void mark(Job job, JobMark.Kind kind, long pos, boolean add) {
        var company = job.company();
        if (company != null) marks.put(new JobMark(job.id(), company, kind, pos), add);
    }

    /**
     * Drops the work counted for a job that is no longer the company's, such as one given back on this server, and the
     * positions remembered for it.
     *
     * @param job id of the job
     */
    public void forget(int job) {
        pending.keySet().removeIf(work -> work.job() == job);
        marks.keySet().removeIf(mark -> mark.job() == job);
        placed.remove(job);
        filled.remove(job);
        supplied.remove(job);
        owners.remove(job);
        cleared.add(job);
    }

    /**
     * Holds players who only have access through a job to its area and keeps others from placing blocks in a taken
     * job's area.
     *
     * @param event the placement
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        var block = event.getBlockPlaced();
        if (held(event.getPlayer(), block, true)
                || (block.getType() == Material.TNT && danger(event.getPlayer(), block))
                || (HarvestRules.crop(block.getType()) != HarvestRules.Crop.FIELD
                        && harvesting(event.getPlayer(), block))) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof MagicBuilding.Placing) return;
        var misplaced = misplaced(event.getPlayer(), block);
        if (misplaced == null && event instanceof BlockMultiPlaceEvent multi) {
            for (var state : multi.getReplacedBlockStates()) {
                misplaced = misplaced(event.getPlayer(), state.getBlock());
                if (misplaced != null) break;
            }
        }
        if (misplaced != null) {
            event.setCancelled(true);
            if (misplaced.supplied()) {
                onlyMagic.accept(event.getPlayer());
            } else {
                refused.accept(event.getPlayer(), misplaced.want());
            }
        }
    }

    /**
     * @param player a player placing a block
     * @param block  the block as placed
     * @return what the blueprint asks for at the block's position when the block lies in the area of a taken build job
     *     the player works on and is neither that nor a block of the build whitelist, and any block but those of the
     *     whitelist in the area of a job whose owner supplies the materials; null for any other block, a blueprint not
     *     read yet and a player with the bypass permission
     */
    private @Nullable Misplaced misplaced(Player player, Block block) {
        var world = block.getWorld().getName();
        for (var entry : active) {
            var job = entry.job();
            if (job.kind() != JobKind.BUILD
                    || job.state() != JobState.TAKEN
                    || !job.area().contains(world, block.getX(), block.getY(), block.getZ())
                    || !entry.members().contains(player.getUniqueId())) {
                continue;
            }
            var blueprint = blueprints.known(job.id());
            if (blueprint.isEmpty()) return null;
            var want = blueprint.get().want(job.area(), block.getX(), block.getY(), block.getZ());
            var type = block.getType();
            if (player.hasPermission(Permission.Admin.JOBS_BYPASS)) return null;
            if (job.supplied() && counted(job.id(), key(block))) return new Misplaced(want, true);
            if (whitelist.get().contains(type)) return null;
            if (job.supplied()) return new Misplaced(want, true);
            if (want != null && want.type() == type) return null;
            return new Misplaced(want, false);
        }
        return null;
    }

    /**
     * Notes a block placed in a taken job's area, so breaking it again does not count as work. In a terraform job's
     * area a member placing a block that fills its position as the target asks counts it as their work, once for each
     * position, and so does one placed as the blueprint of a build job asks, turned as asked when it was not.
     *
     * @param event the placement, as it stands after every plugin had its say
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(BlockPlaceEvent event) {
        var block = event.getBlockPlaced();
        var world = block.getWorld().getName();
        var worker = event.getPlayer().getUniqueId();
        for (var entry : active) {
            var job = entry.job();
            if (!job.area().contains(world, block.getX(), block.getY(), block.getZ())) continue;
            var key = key(block);
            if (placed.computeIfAbsent(job.id(), id -> ConcurrentHashMap.newKeySet())
                    .add(key)) {
                mark(job, JobMark.Kind.PLACED, key, true);
            }
            var company = job.company();
            if (company == null || !entry.members().contains(worker)) return;
            if (job.kind() == JobKind.BUILD) turn(job, block);
            if (!fills(job, block, true)) return;
            if (filled.computeIfAbsent(job.id(), id -> ConcurrentHashMap.newKeySet())
                    .add(key)) {
                mark(job, JobMark.Kind.FILLED, key, true);
                pending.merge(new Work(job.id(), company, worker), 1, Integer::sum);
            }
            return;
        }
    }

    /**
     * Holds players who only have access through a job to its area.
     *
     * @param event the break
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHold(BlockBreakEvent event) {
        if (held(event.getPlayer(), event.getBlock(), false) || shaping(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param player a player
     * @param block  a block
     * @return true when the block lies in the area of a taken build or terraform job the player does not work on, where
     *     only the working company may break blocks, so nobody undoes its work; never with the bypass permission, and
     *     never for a block placed from the stock of a supplied job, which stays counted as placed
     */
    private boolean shaping(Player player, Block block) {
        var world = block.getWorld().getName();
        for (var entry : active) {
            var job = entry.job();
            if ((job.kind() == JobKind.BUILD || job.kind() == JobKind.TERRAFORM)
                    && job.state() == JobState.TAKEN
                    && job.area().contains(world, block.getX(), block.getY(), block.getZ())
                    && !entry.members().contains(player.getUniqueId())) {
                if (job.supplied() && counted(job.id(), key(block))) return false;
                return !player.hasPermission(Permission.Admin.JOBS_BYPASS);
            }
        }
        return false;
    }

    /**
     * Holds players who only have access through a job to its area, keeps others from pouring into a taken job's area
     * and keeps lava out of it.
     *
     * @param event the emptied bucket
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent event) {
        var player = event.getPlayer();
        if (held(player, event.getBlock(), true)
                || (event.getBucket() == Material.LAVA_BUCKET && danger(player, event.getBlock()))
                || harvesting(player, event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (supplying(player, event.getBlock())) {
            event.setCancelled(true);
            onlyMagic.accept(player);
        }
    }

    /**
     * @param player a player
     * @param block  a block
     * @return true when the block lies in the area of a taken build job whose owner supplies the materials and the
     *     player works on it without the bypass permission, so only magic blocks build there
     */
    private boolean supplying(Player player, Block block) {
        return supplied(player, block).isPresent() && !player.hasPermission(Permission.Admin.JOBS_BYPASS);
    }

    /**
     * @param player a player
     * @param block  a block
     * @return the taken build job whose owner supplies the materials, the player works on and whose area holds the
     *     block
     */
    public Optional<Active> supplied(Player player, Block block) {
        var world = block.getWorld().getName();
        for (var entry : active) {
            var job = entry.job();
            if (job.kind() == JobKind.BUILD
                    && job.supplied()
                    && job.state() == JobState.TAKEN
                    && job.area().contains(world, block.getX(), block.getY(), block.getZ())
                    && entry.members().contains(player.getUniqueId())) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * Holds players who only have access through a job to its area.
     *
     * @param event the filled bucket
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        if (held(event.getPlayer(), event.getBlock(), false)) event.setCancelled(true);
    }

    /**
     * Keeps players from setting fire in a taken job's area.
     *
     * @param event the fire
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        var player = event.getPlayer();
        if (player != null && danger(player, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Holds players who only have access through a job to its area when they use a block: they may still open doors,
     * gates and trapdoors and use the item in their hand, but not containers, switches or pressure plates.
     *
     * @param event the use
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (block == null || !held(event.getPlayer(), block, false)) return;
        if (event.getAction() != Action.PHYSICAL
                && block.getBlockData() instanceof Openable
                && !(block.getState(false) instanceof Container)) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    /**
     * Keeps the fields of taken harvest jobs from being trampled.
     *
     * @param event the step
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onTrample(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (event.getAction() == Action.PHYSICAL
                && block != null
                && block.getType() == Material.FARMLAND
                && harvestArea(block) != null) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    /**
     * Keeps mobs from trampling the farmland of a harvest area.
     *
     * @param event a mob stepping on a block
     */
    @EventHandler(ignoreCancelled = true)
    public void onMobTrample(EntityInteractEvent event) {
        var block = event.getBlock();
        if (block.getType() == Material.FARMLAND && harvestArea(block) != null) event.setCancelled(true);
    }

    /**
     * Picks sweet berries and glow berries a member of the working company uses in a harvest area: they go into the
     * stock and are paid like other crops, and the plant stays to grow again. Using them with the other hand does
     * nothing, so bone meal can not be used on them either.
     *
     * @param event the use of a block
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPick(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || block == null
                || HarvestRules.crop(block.getType()) != HarvestRules.Crop.PICKED) {
            return;
        }
        var entry = harvestArea(block);
        if (entry == null) return;
        var company = entry.job().company();
        var player = event.getPlayer();
        if (company == null || !entry.members().contains(player.getUniqueId())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        var data = block.getBlockData();
        var isBush = block.getType() == Material.SWEET_BERRY_BUSH;
        var age = isBush && data instanceof Ageable bush ? bush.getAge() : 0;
        var berries = data instanceof CaveVinesPlant vine && vine.isBerries();
        var items = HarvestRules.picked(
                block.getType(), age, berries, ThreadLocalRandom.current().nextBoolean());
        if (items.isEmpty()) return;
        if (isBush && data instanceof Ageable bush) bush.setAge(1);
        if (!isBush && data instanceof CaveVinesPlant vine) vine.setBerries(false);
        block.setBlockData(data);
        harvested.merge(new Work(entry.job().id(), company, player.getUniqueId()), new HashMap<>(items), (old, add) -> {
            add.forEach((name, amount) -> old.merge(name, amount, Integer::sum));
            return old;
        });
    }

    /**
     * Harvests for a member of the company working on a harvest job, by the rules of {@link HarvestRules}: the block
     * is never broken as such and drops nothing. A ripe field crop is replanted at once and its yield, less one seed,
     * goes into the stock; sugar cane, bamboo, cactus and kelp above their base are taken off with everything above,
     * kelp leaving water and its base growing on as the top; a melon or pumpkin is taken whole. Berries are picked by
     * using the plant, see {@link #onPick}, so breaking it does nothing. Anything else a member tries to break in the
     * area stays, and so does anything placed while the job ran, so nobody sells goods from elsewhere into the stock; a
     * field crop a member planted is harvested like any other once it is ripe. A column is taken no higher than the top
     * of the area; what grows above it stays without support and drops when it next updates.
     *
     * @param event the break, after the protection plugins had their say
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent event) {
        var block = event.getBlock();
        var entry = harvestArea(block);
        if (entry == null) return;
        var player = event.getPlayer();
        var company = entry.job().company();
        if (company == null || !entry.members().contains(player.getUniqueId())) return;
        event.setCancelled(true);
        var placedHere = placed.getOrDefault(entry.job().id(), Set.of());
        var type = block.getType();
        if (HarvestRules.crop(type) != HarvestRules.Crop.FIELD && placedHere.contains(key(block))) return;
        var tool = player.getInventory().getItemInMainHand();
        Map<String, Integer> items =
                switch (HarvestRules.crop(type)) {
                    case FIELD -> {
                        if (!(block.getBlockData() instanceof Ageable crop) || crop.getAge() < crop.getMaximumAge()) {
                            yield Map.of();
                        }
                        var drops = block.getDrops(tool, player);
                        crop.setAge(0);
                        block.setBlockData(crop);
                        yield HarvestRules.yield(type, drops);
                    }
                    case STACKED -> {
                        if (!HarvestRules.sameStack(
                                type, block.getRelative(BlockFace.DOWN).getType())) {
                            yield Map.of();
                        }
                        var height = 0;
                        var top = entry.job().area().maxY() - block.getY();
                        while (height <= top
                                && HarvestRules.sameStack(
                                        type,
                                        block.getRelative(BlockFace.UP, height).getType())
                                && !placedHere.contains(key(block.getRelative(BlockFace.UP, height)))) {
                            height++;
                        }
                        for (var up = height - 1; up >= 0; up--) {
                            block.getRelative(BlockFace.UP, up).setType(HarvestRules.emptied(type), false);
                        }
                        var base = block.getRelative(BlockFace.DOWN);
                        if (base.getType() == Material.KELP_PLANT) base.setType(Material.KELP, false);
                        yield Map.of(HarvestRules.item(type).name(), height);
                    }
                    case FRUIT -> {
                        var drops = block.getDrops(tool, player);
                        block.setType(Material.AIR);
                        yield HarvestRules.yield(type, drops);
                    }
                    case PICKED, NONE -> Map.of();
                };
        if (items.isEmpty()) return;
        harvested.merge(new Work(entry.job().id(), company, player.getUniqueId()), new HashMap<>(items), (old, add) -> {
            add.forEach((name, amount) -> old.merge(name, amount, Integer::sum));
            return old;
        });
    }

    /**
     * @param player a player
     * @param block  a block
     * @return true when the player works on the taken harvest job whose field holds the block, where they only
     *     harvest and plant field crops, so nobody washes a field away or builds on it
     */
    private boolean harvesting(Player player, Block block) {
        var entry = harvestArea(block);
        return entry != null
                && entry.members().contains(player.getUniqueId())
                && !player.hasPermission(Permission.Admin.JOBS_BYPASS);
    }

    /**
     * @param block a block
     * @return the taken harvest job whose area holds it, null when none does
     */
    private @Nullable Active harvestArea(Block block) {
        var world = block.getWorld().getName();
        for (var entry : active) {
            var job = entry.job();
            if (job.kind() == JobKind.HARVEST
                    && job.state() == JobState.TAKEN
                    && job.area().contains(world, block.getX(), block.getY(), block.getZ())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Holds players who only have access through a job to its area when they use an entity.
     *
     * @param event the use
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onUseEntity(PlayerInteractEntityEvent event) {
        if (held(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), false)) {
            event.setCancelled(true);
        }
    }

    /**
     * Holds players who only have access through a job to its area when they hit an entity, such as an armor stand,
     * by hand or with a projectile. Monsters may still be fought.
     *
     * @param event the hit
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Enemy) return;
        if (player(event.getDamager()) instanceof Player player
                && held(player, event.getEntity().getLocation().getBlock(), false)) {
            event.setCancelled(true);
        }
    }

    /**
     * Holds players who only have access through a job to its area when they break a painting or an item frame.
     *
     * @param event the break
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent event) {
        if (player(event.getRemover()) instanceof Player player
                && held(player, event.getEntity().getLocation().getBlock(), false)) {
            event.setCancelled(true);
        }
    }

    /**
     * Counts a block a member of the working company breaks in a taken job's area, unless it was placed during the job.
     *
     * @param event the break
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        var block = event.getBlock();
        var worker = event.getPlayer().getUniqueId();
        var world = block.getWorld().getName();
        for (var entry : active) {
            if (!entry.job().area().contains(world, block.getX(), block.getY(), block.getZ())) continue;
            var positions = placed.get(entry.job().id());
            if (positions != null && positions.remove(key(block))) {
                mark(entry.job(), JobMark.Kind.PLACED, key(block), false);
                return;
            }
            if (!entry.members().contains(worker)) return;
            var company = entry.job().company();
            if (company == null) return;
            if (!removing(entry.job(), block)) {
                if (fills(entry.job(), block, false)
                        && filled.computeIfAbsent(entry.job().id(), id -> ConcurrentHashMap.newKeySet())
                                .add(key(block))) {
                    mark(entry.job(), JobMark.Kind.FILLED, key(block), true);
                }
                return;
            }
            if (dug(entry.job(), block)) return;
            pending.merge(new Work(entry.job().id(), company, worker), 1, Integer::sum);
            return;
        }
    }

    /**
     * @param job   a taken job
     * @param block a block of its area a member breaks where the job asks for it to go
     * @return true when it lies where a build job digs out and that position was counted before, so a block that fell or
     *     grew there earns nothing; the position is noted as counted otherwise
     */
    private boolean dug(Job job, Block block) {
        if (job.kind() != JobKind.BUILD) return false;
        var wanted = wanted(job, block);
        if (wanted == null || !wanted.type().isAir()) return false;
        var key = key(block);
        if (!filled.computeIfAbsent(job.id(), id -> ConcurrentHashMap.newKeySet())
                .add(key)) return true;
        mark(job, JobMark.Kind.FILLED, key, true);
        return false;
    }

    /**
     * @param job   a taken job
     * @param block a block of its area, before it is broken
     * @return whether breaking it is work the job asks for: any block of a dig job; for a terraform job one above the
     *     target height, or one of the top layer that is not the surface block the target names; for a build job one
     *     where the blueprint asks for another block
     */
    private boolean removing(Job job, Block block) {
        if (job.kind() == JobKind.BUILD) {
            var wanted = wanted(job, block);
            return wanted != null && !matches(wanted, block);
        }
        var target = job.target();
        if (job.kind() != JobKind.TERRAFORM || target == null) return true;
        if (block.getY() > target.y()) return true;
        if (block.getY() < target.y()) return false;
        var surface = TerraformCheck.surface(target);
        return surface != null && block.getType() != surface;
    }

    /**
     * @param job    a taken job
     * @param block  a block of its area
     * @param turned whether a block of a build job that is turned another way counts, as it is turned as asked
     * @return whether the block is as the job asks at its position: as the target of a terraform job, or the block the
     *     blueprint of a build job asks for; never for other kinds
     */
    private boolean fills(Job job, Block block, boolean turned) {
        if (job.kind() == JobKind.BUILD) {
            var wanted = wanted(job, block);
            if (wanted == null) return false;
            return matches(wanted, block) || (turned && turned(wanted, block) != null);
        }
        var target = job.target();
        return job.kind() == JobKind.TERRAFORM
                && target != null
                && TerraformCheck.fills(block.getType(), block.getY(), target);
    }

    /**
     * Turns a block placed in a build job's area as the blueprint asks, on the next tick, so the placement is complete.
     *
     * @param job   a taken build job
     * @param block the block as placed
     */
    private void turn(Job job, Block block) {
        var wanted = wanted(job, block);
        if (wanted == null) return;
        var turned = turned(wanted, block);
        if (turned == null) return;
        var type = block.getType();
        mainThread.execute(() -> {
            if (block.getType() != type) return;
            try {
                block.setBlockData(Bukkit.createBlockData(turned), false);
            } catch (IllegalArgumentException e) {
                logger.log(Level.FINE, "Could not turn a block to " + turned, e);
            }
        });
    }

    private static boolean matches(Blueprint.Want wanted, Block block) {
        var type = block.getType();
        if (type != wanted.type()) return false;
        return wanted.fixed().isEmpty()
                || wanted.matches(type, block.getBlockData().getAsString());
    }

    private static @Nullable String turned(Blueprint.Want wanted, Block block) {
        if (block.getType() != wanted.type()) return null;
        return wanted.turn(block.getType(), block.getBlockData().getAsString());
    }

    /**
     * @param job   a build job
     * @param block a block of its area
     * @return the block the job's blueprint asks for there, null for none or a blueprint not read yet
     */
    private Blueprint.@Nullable Want wanted(Job job, Block block) {
        return blueprints
                .known(job.id())
                .map(blueprint -> blueprint.want(job.area(), block.getX(), block.getY(), block.getZ()))
                .orElse(null);
    }

    /**
     * @param player   a player
     * @param block    a block
     * @param building whether the player places blocks or pours, which only the working company may do in a taken
     *                 job's area
     * @return true when the player may not act on the block, never for a player with the bypass permission
     */
    private boolean held(Player player, Block block, boolean building) {
        var uuid = player.getUniqueId();
        if (!outside(uuid, block) && !(building && intruder(uuid, block))) return false;
        return !player.hasPermission(Permission.Admin.JOBS_BYPASS);
    }

    /**
     * @param entity an entity that hit or broke something
     * @return the entity, or the one that shot it when it is a projectile
     */
    private static @Nullable Entity player(@Nullable Entity entity) {
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return entity;
    }

    /**
     * @param player a player
     * @param block  a block
     * @return true when the block lies in a taken job's area and the player has no bypass permission, so lava, TNT and
     *     fire are kept out
     */
    private boolean danger(Player player, Block block) {
        return inArea(block) && !player.hasPermission(Permission.Admin.JOBS_BYPASS);
    }

    /**
     * @param block a block
     * @return true when the block lies in a taken job's area
     */
    private boolean inArea(Block block) {
        var world = block.getWorld().getName();
        for (var entry : active) {
            if (entry.job().area().contains(world, block.getX(), block.getY(), block.getZ())) return true;
        }
        return false;
    }

    /**
     * @param player a player
     * @param block  a block
     * @return true when the player only has access to the block's land through a job and the block lies outside the
     *     areas of the jobs the player works on
     */
    boolean outside(UUID player, Block block) {
        var world = block.getWorld().getName();
        var current = active;
        for (var entry : current) {
            if (entry.members().contains(player)
                    && entry.job().area().contains(world, block.getX(), block.getY(), block.getZ())) {
                return false;
            }
        }
        for (var entry : current) {
            if (!entry.granted().contains(player)) continue;
            if (hooks.reaches(entry.job(), world, block.getX(), block.getY(), block.getZ())) return true;
        }
        return false;
    }

    /**
     * @param player a player
     * @param block  a block
     * @return true when the block lies in a taken job's area the player does not work on
     */
    boolean intruder(UUID player, Block block) {
        var world = block.getWorld().getName();
        for (var entry : active) {
            if (entry.job().area().contains(world, block.getX(), block.getY(), block.getZ())
                    && !entry.members().contains(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param block a block
     * @return its position packed into one number, as x and z in 26 bits and y in 12 bits each
     */
    static long key(Block block) {
        return key(block.getX(), block.getY(), block.getZ());
    }

    /**
     * @param x x of a block
     * @param y y of a block
     * @param z z of a block
     * @return its position packed into one number, as {@link #key(Block)}
     */
    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) y & 0xFFF);
    }

    /**
     * @param job id of a job
     * @param key a position, as {@link #key(Block)} packs it
     * @return whether a block was placed there from the job's stock and counts as placed
     */
    public boolean counted(int job, long key) {
        var positions = supplied.get(job);
        return positions != null && positions.contains(key);
    }

    /**
     * Notes a block a member placed from the stock of a supplied job: it counts as placed and, once for each position,
     * as their work, and breaking it again is no work.
     *
     * @param entry  the job
     * @param block  the block as placed
     * @param worker the member
     */
    public void placedFromStock(Active entry, Block block, UUID worker) {
        var job = entry.job();
        var company = job.company();
        if (company == null) return;
        var key = key(block);
        if (supplied.computeIfAbsent(job.id(), id -> ConcurrentHashMap.newKeySet())
                .add(key)) {
            mark(job, JobMark.Kind.SUPPLIED, key, true);
        }
        if (placed.computeIfAbsent(job.id(), id -> ConcurrentHashMap.newKeySet())
                .add(key)) {
            mark(job, JobMark.Kind.PLACED, key, true);
        }
        if (filled.computeIfAbsent(job.id(), id -> ConcurrentHashMap.newKeySet())
                .add(key)) {
            mark(job, JobMark.Kind.FILLED, key, true);
            pending.merge(new Work(job.id(), company, worker), 1, Integer::sum);
        }
    }

    /**
     * Opens a position of a supplied job again, as a member broke the block placed there from the stock.
     *
     * @param job   the job
     * @param block the block
     * @return whether a block placed from the stock counted there
     */
    public boolean takenBack(Job job, Block block) {
        var key = key(block);
        var positions = supplied.get(job.id());
        if (positions == null || !positions.remove(key)) return false;
        mark(job, JobMark.Kind.SUPPLIED, key, false);
        return true;
    }

    /**
     * @return whether a check found the jobs of this server's areas since the plugin started
     */
    public boolean loaded() {
        return loaded;
    }

    /**
     * @return the taken jobs of this server's areas as the last check found them
     */
    public List<Active> active() {
        return active;
    }

    /**
     * @param job id of a job
     * @return whether it is taken and worked on in an area of this server
     */
    public boolean working(int job) {
        for (var entry : active) {
            if (entry.job().id() == job && entry.job().state() == JobState.TAKEN) return true;
        }
        return false;
    }

    private <T> T onMain(Supplier<T> task, T otherwise) {
        try {
            return CompletableFuture.supplyAsync(task, mainThread)
                    .orTimeout(MAIN_THREAD_SECONDS, TimeUnit.SECONDS)
                    .join();
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "A land hook did not answer on the main thread", e);
            return otherwise;
        }
    }

    /**
     * A taken job, the members who may work on it, and the players who only have access through it.
     *
     * @param job     the job
     * @param members the members of its company
     * @param granted the players a land hook gave access to for the job
     */
    public record Active(Job job, Set<UUID> members, Set<UUID> granted) {
        /**
         * @param job     the job
         * @param members the members of its company
         * @param granted the players a land hook gave access to for the job
         */
        public Active {
            members = Set.copyOf(members);
            granted = Set.copyOf(granted);
        }
    }

    private record Work(int job, int company, UUID worker) {}

    private record Crops(int job, int company) {}

    private record Misplaced(Blueprint.@Nullable Want want, boolean supplied) {}

    private record Plan(Job job, Set<UUID> members, Set<UUID> before) {}

    private record Answer(boolean owned, Set<UUID> planned) {}

    private record Applied(Set<Integer> revoked, Map<Integer, Set<UUID>> given) {}

    /**
     * Stores and pays what a member harvested on a harvest job.
     */
    @FunctionalInterface
    public interface Harvests {
        /**
         * @param job      id of the job
         * @param company  id of the company the members harvested for
         * @param byWorker the items of each member, as amounts by material name
         */
        void harvested(int job, int company, Map<UUID, Map<String, Integer>> byWorker);
    }
}
