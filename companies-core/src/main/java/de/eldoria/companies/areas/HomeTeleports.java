/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import com.google.common.io.ByteStreams;
import de.eldoria.companies.configuration.elements.areas.AreaSettings;
import de.eldoria.companies.data.repository.AHomeData;
import de.eldoria.companies.data.wrapper.area.CompanyHome;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Teleports members to their company's home. The player stands still for the warm-up of {@code areas.yml}; moving to
 * another block, taking damage or leaving cancels it. Then the cost is paid, by the player or by the treasury, and the
 * player is teleported. A home on another server is reached through the proxy: the teleport is noted in the database
 * for {@value #TRAVEL_SECONDS} seconds, the player is sent to the home's server with the {@code BungeeCord} plugin
 * channel (which Velocity understands too), and that server teleports them when they arrive. When the warm-up ends,
 * whether the player still belongs to the company and where its home is now are read again. A player waits for one
 * teleport at a time, so nothing is paid twice. The cooldown starts with a teleport that was paid; a teleport that
 * fails gives its cost and cooldown back. Whether the proxy really moves the player can not be told. The cooldown is
 * kept per server. Runs on the main thread; the database is reached off it.
 */
public final class HomeTeleports implements Listener {
    /**
     * How long a teleport to another server waits for the player to arrive there, in seconds.
     */
    public static final int TRAVEL_SECONDS = 60;

    /**
     * The plugin channel of the proxy.
     */
    public static final String CHANNEL = "BungeeCord";

    private final Plugin plugin;
    private final AHomeData homes;
    private final Supplier<AreaSettings> settings;
    private final UUID node;
    private final Recheck recheck;
    private final Payments payments;
    private final Tell tell;
    private final Executor background;
    private final Clock clock;
    private final Logger logger;
    private final Map<UUID, Warmup> warmups = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> cooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    /**
     * @param plugin     the plugin
     * @param homes      the homes and pending teleports
     * @param settings   the settings of areas
     * @param node       this server
     * @param recheck    reads where a member's home is when the warm-up ends
     * @param payments   takes the cost of a teleport
     * @param tell       tells a player how the teleport goes
     * @param background runs the database calls off the main thread
     * @param clock      the clock, in UTC
     * @param logger     reports failures
     */
    public HomeTeleports(
            Plugin plugin,
            AHomeData homes,
            Supplier<AreaSettings> settings,
            UUID node,
            Recheck recheck,
            Payments payments,
            Tell tell,
            Executor background,
            Clock clock,
            Logger logger) {
        this.plugin = plugin;
        this.homes = homes;
        this.settings = settings;
        this.node = node;
        this.recheck = recheck;
        this.payments = payments;
        this.tell = tell;
        this.background = background;
        this.clock = clock;
        this.logger = logger;
    }

    /**
     * Starts the teleport of a member to their company's home.
     *
     * @param player  the player
     * @param company the player's company
     * @param home    its home
     * @return whether the teleport started, or why not
     */
    public Start start(Player player, CompanyProfile company, CompanyHome home) {
        var uuid = player.getUniqueId();
        if (busy.contains(uuid)) return new Start.Refused(Refusal.WAITING, 0);
        var until = cooldowns.get(uuid);
        var now = clock.instant();
        if (until != null && until.isAfter(now)) {
            return new Start.Refused(
                    Refusal.COOLDOWN, Duration.between(now, until).toSeconds() + 1);
        }
        var local = home.node().equals(node);
        if (!local && home.server().isEmpty()) return new Start.Refused(Refusal.UNREACHABLE, 0);
        if (local && plugin.getServer().getWorld(home.world()) == null) {
            return new Start.Refused(Refusal.NO_WORLD, 0);
        }
        busy.add(uuid);
        var seconds = settings.get().homeWarmupSeconds();
        if (seconds == 0) {
            go(player, company);
            return new Start.Started(0);
        }
        var task = plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (warmups.remove(uuid) == null) return;
                            if (player.isOnline()) go(player, company);
                            else busy.remove(uuid);
                        },
                        seconds * 20L);
        warmups.put(uuid, new Warmup(player.getLocation().getBlock().getLocation(), task));
        return new Start.Started(seconds);
    }

    /**
     * Cancels a warm-up when the player moves to another block.
     *
     * @param event the move
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        var warmup = warmups.get(event.getPlayer().getUniqueId());
        if (warmup == null) return;
        var to = event.getTo().getBlock().getLocation();
        if (!to.equals(warmup.start())) cancel(event.getPlayer(), Notice.MOVED);
    }

    /**
     * Cancels a warm-up when the player takes damage.
     *
     * @param event the damage
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && warmups.containsKey(player.getUniqueId())) {
            cancel(player, Notice.HURT);
        }
    }

    /**
     * Drops the warm-up of a player who leaves.
     *
     * @param event the leaving
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        var warmup = warmups.remove(uuid);
        if (warmup == null) return;
        warmup.task().cancel();
        busy.remove(uuid);
    }

    /**
     * Teleports a player who arrives from another server on the way to their company's home here.
     *
     * @param event the join
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();
        CompletableFuture.supplyAsync(
                        () -> {
                            var company = homes.arrived(uuid, node, now());
                            return company.isEmpty()
                                    ? null
                                    : homes.home(company.getAsInt()).orElse(null);
                        },
                        background)
                .thenAccept(home -> {
                    if (home == null || !plugin.isEnabled()) return;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> arrive(player, home), 5L);
                })
                .exceptionally(err -> {
                    logger.log(Level.WARNING, "Could not read the home teleport of " + player.getName(), err);
                    return null;
                });
    }

    /**
     * Cancels every warm-up, when the plugin stops.
     */
    public void stop() {
        warmups.values().forEach(warmup -> warmup.task().cancel());
        warmups.clear();
        busy.clear();
    }

    private void cancel(Player player, Notice why) {
        var warmup = warmups.remove(player.getUniqueId());
        if (warmup == null) return;
        warmup.task().cancel();
        busy.remove(player.getUniqueId());
        tell.tell(player, why, "");
    }

    /**
     * Reads again, off the main thread, whether the player still belongs to the company and where its home is now,
     * then pays.
     */
    private void go(Player player, CompanyProfile company) {
        var uuid = player.getUniqueId();
        CompletableFuture.supplyAsync(() -> recheck.current(uuid, company.id()), background)
                .whenComplete((home, err) -> onMain(uuid, () -> {
                    if (err != null) logger.log(Level.WARNING, "Could not read the home of " + company.id(), err);
                    if (err != null || home == null || home.isEmpty()) {
                        done(uuid);
                        if (player.isOnline()) tell.tell(player, Notice.GONE, "");
                        return;
                    }
                    if (!player.isOnline()) {
                        done(uuid);
                        return;
                    }
                    if (!home.get().node().equals(node) && home.get().server().isEmpty()) {
                        done(uuid);
                        tell.tell(player, Notice.UNREACHABLE, "");
                        return;
                    }
                    pay(player, company, home.get());
                }));
    }

    private void pay(Player player, CompanyProfile company, CompanyHome home) {
        var uuid = player.getUniqueId();
        var current = settings.get();
        var cost = current.homeCost();
        if (cost <= 0) {
            travel(player, company, home, Paid.NOTHING);
            return;
        }
        if (!current.homePaidByTreasury()) {
            if (!payments.charge(player, cost)) {
                done(uuid);
                tell.tell(player, Notice.FUNDS, "");
                return;
            }
            travel(player, company, home, new Paid(Payer.PLAYER, cost));
            return;
        }
        var paid = new Paid(Payer.TREASURY, cost);
        payments.chargeTreasury(company, player, cost).whenComplete((charged, err) -> {
            var took = err == null && Boolean.TRUE.equals(charged);
            if (!plugin.isEnabled()) {
                if (took) payments.refundTreasury(company, player, cost);
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!took) {
                    done(uuid);
                    if (player.isOnline()) tell.tell(player, Notice.TREASURY_FUNDS, "");
                    return;
                }
                if (!player.isOnline()) {
                    undo(player, company, paid);
                    done(uuid);
                    return;
                }
                travel(player, company, home, paid);
            });
        });
    }

    private void travel(Player player, CompanyProfile company, CompanyHome home, Paid paid) {
        var uuid = player.getUniqueId();
        cooldowns.put(uuid, clock.instant().plusSeconds(settings.get().homeCooldownSeconds()));
        if (home.node().equals(node)) {
            if (!arrive(player, home)) undo(player, company, paid);
            done(uuid);
            return;
        }
        var expires = now().plusSeconds(TRAVEL_SECONDS);
        CompletableFuture.supplyAsync(() -> homes.travelling(uuid, company.id(), home.node(), expires), background)
                .whenComplete((noted, err) -> onMain(uuid, () -> {
                    done(uuid);
                    if (err != null || !Boolean.TRUE.equals(noted)) {
                        if (err != null) logger.log(Level.WARNING, "Could not note the teleport of " + uuid, err);
                        undo(player, company, paid);
                        if (player.isOnline()) tell.tell(player, Notice.FAILED, "");
                        return;
                    }
                    if (!player.isOnline()) {
                        undo(player, company, paid);
                        return;
                    }
                    tell.tell(player, Notice.SENDING, home.server());
                    var out = ByteStreams.newDataOutput();
                    out.writeUTF("Connect");
                    out.writeUTF(home.server());
                    player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
                }));
    }

    /**
     * Gives back what a teleport that did not happen cost, and its cooldown.
     */
    private void undo(Player player, CompanyProfile company, Paid paid) {
        cooldowns.remove(player.getUniqueId());
        switch (paid.payer()) {
            case PLAYER -> payments.refundPlayer(player, paid.amount());
            case TREASURY -> payments.refundTreasury(company, player, paid.amount());
            case NONE -> {}
        }
    }

    private void done(UUID player) {
        busy.remove(player);
    }

    /**
     * Runs a step on the main thread while the plugin runs; a step after it stopped only frees the player.
     */
    private void onMain(UUID player, Runnable step) {
        if (!plugin.isEnabled()) {
            done(player);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, step);
    }

    /**
     * @return whether the player is at the home now
     */
    private boolean arrive(Player player, CompanyHome home) {
        if (!player.isOnline()) return false;
        var world = plugin.getServer().getWorld(home.world());
        if (world == null) {
            tell.tell(player, Notice.NO_WORLD, home.world());
            return false;
        }
        if (!player.teleport(new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch()))) {
            tell.tell(player, Notice.FAILED, "");
            return false;
        }
        tell.tell(player, Notice.ARRIVED, "");
        return true;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).withNano(0);
    }

    /**
     * Takes the cost of a teleport home. Vault is only called on the main thread.
     */
    public interface Payments {
        /**
         * @param player the player paying
         * @param amount the cost
         * @return true when the player had it and it was taken
         */
        boolean charge(Player player, double amount);

        /**
         * @param company the company paying
         * @param player  the member travelling, named in the treasury log
         * @param amount  the cost
         * @return completes with true when the treasury had it and it was taken
         */
        CompletableFuture<Boolean> chargeTreasury(CompanyProfile company, Player player, double amount);

        /**
         * Gives a player back what they paid for a teleport that did not happen.
         *
         * @param player the player
         * @param amount the cost
         */
        void refundPlayer(Player player, double amount);

        /**
         * Gives the treasury back what it paid for a teleport that did not happen.
         *
         * @param company the company
         * @param player  the member
         * @param amount  the cost
         */
        void refundTreasury(CompanyProfile company, Player player, double amount);
    }

    /**
     * Reads, off the main thread, where a member's home is when their warm-up ends.
     */
    @FunctionalInterface
    public interface Recheck {
        /**
         * @param player  the member
         * @param company id of the company the teleport started for
         * @return the company's home now, empty when the player left the company or it has no home anymore
         */
        Optional<CompanyHome> current(UUID player, int company);
    }

    /**
     * Tells a player how a teleport home goes.
     */
    @FunctionalInterface
    public interface Tell {
        /**
         * @param player the player
         * @param notice what happened
         * @param detail the server or world it names, empty when it names none
         */
        void tell(Player player, Notice notice, String detail);
    }

    /**
     * What a player is told while travelling home.
     */
    public enum Notice {
        /**
         * The player moved during the warm-up.
         */
        MOVED,
        /**
         * The player took damage during the warm-up.
         */
        HURT,
        /**
         * The player can not pay the teleport.
         */
        FUNDS,
        /**
         * The treasury can not pay the teleport.
         */
        TREASURY_FUNDS,
        /**
         * The player is being sent to the home's server; the detail names it.
         */
        SENDING,
        /**
         * The player is at the home.
         */
        ARRIVED,
        /**
         * The world of the home is not loaded here; the detail names it.
         */
        NO_WORLD,
        /**
         * The teleport could not be prepared or the player could not be moved; what it cost went back.
         */
        FAILED,
        /**
         * The player left the company or its home is gone, so the teleport is called off.
         */
        GONE,
        /**
         * The home was moved during the warm-up to a server that can not be reached from here.
         */
        UNREACHABLE
    }

    /**
     * Why a teleport did not start.
     */
    public enum Refusal {
        /**
         * The player waits for a teleport already.
         */
        WAITING,
        /**
         * The player teleported home a moment ago.
         */
        COOLDOWN,
        /**
         * The home is on another server that has no name in the proxy.
         */
        UNREACHABLE,
        /**
         * The home's world is not loaded on this server.
         */
        NO_WORLD
    }

    /**
     * Whether a teleport started.
     */
    public sealed interface Start {
        /**
         * The teleport started.
         *
         * @param seconds the warm-up, 0 when the player was teleported at once
         */
        record Started(int seconds) implements Start {}

        /**
         * The teleport did not start.
         *
         * @param refusal why
         * @param seconds the cooldown left, 0 for other refusals
         */
        record Refused(Refusal refusal, long seconds) implements Start {}
    }

    private record Warmup(Location start, BukkitTask task) {}

    private enum Payer {
        NONE,
        PLAYER,
        TREASURY
    }

    private record Paid(Payer payer, double amount) {
        static final Paid NOTHING = new Paid(Payer.NONE, 0);
    }
}
