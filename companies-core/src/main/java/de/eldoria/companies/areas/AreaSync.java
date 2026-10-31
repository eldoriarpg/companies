/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.AreaSource;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ICompanyMember;
import de.eldoria.companies.data.repository.AAreaData;
import de.eldoria.companies.data.wrapper.area.AreaOffer;
import de.eldoria.companies.data.wrapper.area.CompanyArea;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.events.company.CompanyJoinEvent;
import de.eldoria.companies.events.company.CompanyKickEvent;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.companies.events.company.CompanyMemberPermissionEvent;
import de.eldoria.companies.events.company.CompanyOwnerTransferEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the areas of this server's worlds in step with their companies. Every check reads the areas of this server
 * and their companies off the main thread, then asks the providers in one round on the main thread:
 * <ul>
 *     <li>A linked area whose member left the company, who no longer owns it, that is gone or whose company is gone is
 *     unlinked once checks found it so for at least {@value #CHECK_SECONDS} seconds in a row, so a plugin that answers
 *     late does not cost an area.</li>
 *     <li>An area its provider names another way now, as merged plots, is stored under that name. When two areas of
 *     the company became one, the other is dropped and its builders kept on the one left.</li>
 *     <li>Members with {@code BUILD} who can not build there yet are made builders, and builders Companies made who
 *     lost {@code BUILD} or left are taken off again. A builder the area's owner added themselves is never taken off,
 *     which is why Companies notes the builders it adds.</li>
 *     <li>A bought or rented area belongs to the owner of its company alone and follows a new owner. Every builder
 *     who is not a member with {@code BUILD} and not working on a job there is taken off. When its rent ended, or its
 *     company is gone for at least {@value #CHECK_SECONDS} seconds, it goes back to the keeper of its offer; a provider
 *     that can not take it back is asked again on the next check.</li>
 * </ul>
 * The answers are written back off the main thread, whenever the main thread gets to the round. A check that sees a
 * query fail while reading changes nothing, because a failed read looks like a company or member that is gone. Areas
 * of a provider that is not there or of a world that is not loaded are left alone until they are back. Checks run
 * every {@value #CHECK_SECONDS} seconds and soon after a member joins, leaves, is kicked or gets other permissions on
 * this server; checks, unlinking and giving back run one at a time. Each check also renews what the container rule
 * reads on the main thread: which company an area belongs to and the permissions of its members.
 */
public final class AreaSync implements Listener {
    /**
     * How often the areas are checked, in seconds.
     */
    public static final int CHECK_SECONDS = 30;

    private final AreaProviders providers;
    private final AAreaData areas;
    private final IntFunction<Optional<CompanyProfile>> companies;
    private final LongSupplier queryErrors;
    private final UUID node;
    private final Executor mainThread;
    private final Logger logger;
    private final Consumer<CompanyProfile> changed;
    private final Supplier<Map<String, Set<UUID>>> jobBuilders;
    private final AtomicBoolean queued = new AtomicBoolean();
    private final InstantSource clock;
    private final Set<CompletableFuture<?>> waiting = ConcurrentHashMap.newKeySet();
    private volatile boolean stopped;
    private Map<Integer, Instant> suspected = Map.of();
    private volatile Map<String, Integer> owners = Map.of();
    private volatile Map<Integer, Map<UUID, ICompanyMember>> members = Map.of();
    private volatile ScheduledExecutorService executor;

    /**
     * @param providers   the plot and claim plugins
     * @param areas       the areas of companies
     * @param companies   a company by its id, empty when it is gone
     * @param queryErrors how many database queries failed so far
     * @param clock       tells how long an area has looked lost
     * @param node        this server
     * @param mainThread  runs the provider calls on the main thread
     * @param logger      reports failures
     * @param changed     told about a company whose areas a check changed
     * @param jobBuilders the players running jobs made builders, by the area of the job as {@code provider:ref}
     */
    public AreaSync(
            AreaProviders providers,
            AAreaData areas,
            IntFunction<Optional<CompanyProfile>> companies,
            LongSupplier queryErrors,
            InstantSource clock,
            UUID node,
            Executor mainThread,
            Logger logger,
            Consumer<CompanyProfile> changed,
            Supplier<Map<String, Set<UUID>>> jobBuilders) {
        this.providers = providers;
        this.areas = areas;
        this.companies = companies;
        this.queryErrors = queryErrors;
        this.clock = clock;
        this.node = node;
        this.mainThread = mainThread;
        this.logger = logger;
        this.changed = changed;
        this.jobBuilders = jobBuilders;
    }

    /**
     * What a check changes in the builder list of one area.
     *
     * @param add    players to make builders
     * @param remove players Companies made builders who are taken off again
     */
    public record Plan(Set<UUID> add, Set<UUID> remove) {}

    /**
     * Works out how the builders of an area follow its company.
     *
     * @param wanted   members who may build there: those with {@code BUILD} and the owner
     * @param owners   the owners of the area, who build anyway
     * @param builders the builders the provider knows now
     * @param ours     the builders Companies made
     * @return who is added and who is taken off
     */
    public static Plan plan(Set<UUID> wanted, Set<UUID> owners, Set<UUID> builders, Set<UUID> ours) {
        Set<UUID> add = new HashSet<>(wanted);
        add.removeAll(owners);
        add.removeAll(builders);
        Set<UUID> remove = new HashSet<>(ours);
        remove.removeAll(wanted);
        return new Plan(add, remove);
    }

    /**
     * Checks the areas now and then every {@value #CHECK_SECONDS} seconds.
     *
     * @param executor runs the checks
     */
    public void schedule(ScheduledExecutorService executor) {
        this.executor = executor;
        executor.scheduleWithFixedDelay(this::check, 0, CHECK_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Asks for a check soon, after an area or a company changed. Several asks before it runs make one check.
     */
    public void checkSoon() {
        var current = executor;
        if (stopped || current == null || current.isShutdown() || !queued.compareAndSet(false, true)) return;
        try {
            current.execute(this::check);
        } catch (RuntimeException e) {
            queued.set(false);
        }
    }

    /**
     * @param ref an area as found at a location
     * @return id of the company the area belongs to, empty when it belongs to none
     */
    public Optional<Integer> company(AreaRef ref) {
        return Optional.ofNullable(owners.get(ref.toString()));
    }

    /**
     * @param company id of a company
     * @param player  a player
     * @return the player as a member of the company, as the last check found them, empty when they are none
     */
    public Optional<ICompanyMember> member(int company, UUID player) {
        return Optional.ofNullable(members.getOrDefault(company, Map.of()).get(player));
    }

    /**
     * Unlinks an area of this server: the builders Companies gave it are taken off on the main thread, then the area is
     * dropped. Waits for a running check and blocks; call it off the main thread.
     *
     * @param area the area
     * @return true when it was dropped
     */
    public synchronized boolean unlink(CompanyArea area) {
        var ours = areas.builders(area.id());
        onMain(() -> {
            for (var player : ours) {
                providers.call(area.provider(), provider -> provider.removeBuilder(area.ref(), player), false);
            }
            return null;
        });
        var removed = areas.remove(area.id());
        checkSoon();
        return removed;
    }

    /**
     * Gives a bought or rented area of this server back to the server: it goes to the keeper of its offer on the main
     * thread, then the area is dropped. Waits for a running check and blocks; call it off the main thread.
     *
     * @param area the area
     * @return true when the area is back with the server and dropped
     */
    public synchronized boolean giveBack(CompanyArea area) {
        var keeper = keeper(area);
        var released = onMain(() -> release(area, keeper));
        if (!released) return false;
        var removed = areas.remove(area.id());
        checkSoon();
        return removed;
    }

    private @Nullable UUID keeper(CompanyArea area) {
        return areas.offer(area.offer()).map(AreaOffer::keeper).orElse(null);
    }

    private boolean release(CompanyArea area, @Nullable UUID keeper) {
        var released = providers.call(area.provider(), provider -> provider.release(area.ref(), keeper), false);
        if (!released) {
            logger.warning("Could not give company area " + area.id() + " (" + area.provider() + " " + area.ref()
                    + ") back to the server; trying again on the next check");
        }
        return released;
    }

    /**
     * Checks the areas of this server: unlinks lost ones, gives back what returns to the server, renames merged ones
     * and brings their owners and builders in step.
     */
    public synchronized void check() {
        queued.set(false);
        try {
            var errors = queryErrors.getAsLong();
            var found = areas.ofNode(node);
            Map<Integer, Optional<CompanyProfile>> profiles = new HashMap<>();
            for (var area : found) profiles.computeIfAbsent(area.company(), companies::apply);
            Map<Integer, Set<UUID>> ours = new HashMap<>();
            for (var area : found) ours.put(area.id(), areas.builders(area.id()));
            Map<Integer, UUID> keepers = new HashMap<>();
            for (var area : found) {
                if (!area.fromServer()) continue;
                var keeper = keeper(area);
                if (keeper != null) keepers.put(area.id(), keeper);
            }
            var working = jobBuilders.get();
            if (queryErrors.getAsLong() != errors) {
                logger.warning("Skipped a check of the company areas, the database did not answer every read");
                return;
            }
            var outcome = onMain(() -> new Round(profiles, ours, keepers, working).run(found));
            for (var change : outcome.changes()) change.write(areas);
            for (var company : outcome.touched())
                profiles.getOrDefault(company, Optional.empty()).ifPresent(changed);
            suspected = Map.copyOf(outcome.suspected());
            Map<Integer, Map<UUID, ICompanyMember>> byCompany = new HashMap<>();
            profiles.forEach((id, profile) -> profile.ifPresent(company -> {
                Map<UUID, ICompanyMember> byPlayer = new HashMap<>();
                for (var member : company.members()) byPlayer.put(member.uuid(), member);
                byCompany.put(id, byPlayer);
            }));
            owners = Map.copyOf(outcome.kept());
            members = Map.copyOf(byCompany);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not check the company areas", e);
        }
    }

    /**
     * Runs a task on the main thread and waits for it, however long the main thread takes, so what the task changed
     * at the providers is always written back. Only an interrupt, as when the plugin stops, ends the wait early.
     */
    private <T> T onMain(Supplier<T> task) {
        if (stopped) throw new IllegalStateException("The area sync is stopped");
        var future = CompletableFuture.supplyAsync(task, mainThread);
        waiting.add(future);
        try {
            if (stopped) future.cancel(false);
            return future.get();
        } catch (CancellationException e) {
            throw new IllegalStateException("The area sync stopped while waiting for the main thread", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stopped waiting for the main thread", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("The round on the main thread failed", e.getCause());
        } finally {
            waiting.remove(future);
        }
    }

    /**
     * Stops waiting for the main thread, which runs nothing more once the plugin stops, so a check or an unlink in
     * progress ends at once instead of holding up the shutdown. Takes no new checks.
     */
    public void stop() {
        stopped = true;
        waiting.forEach(future -> future.cancel(false));
    }

    /**
     * One round of a check on the main thread. Areas already under their canonical name go first, so when two stored
     * areas now name the same one, the one already stored under that name stays.
     */
    private final class Round {
        private final Map<Integer, Optional<CompanyProfile>> profiles;
        private final Map<Integer, Set<UUID>> ours;
        private final Map<Integer, UUID> keepers;
        private final Map<String, Set<UUID>> working = new HashMap<>();
        private final Map<String, Set<UUID>> stored;
        private final List<Change> changes = new ArrayList<>();
        private final Map<String, Integer> kept = new HashMap<>();
        private final Map<String, CompanyArea> stayed = new HashMap<>();
        private final Map<Integer, Instant> lost = new HashMap<>();
        private final Set<Integer> touched = new HashSet<>();
        private final Instant now = clock.instant();

        private Round(
                Map<Integer, Optional<CompanyProfile>> profiles,
                Map<Integer, Set<UUID>> ours,
                Map<Integer, UUID> keepers,
                Map<String, Set<UUID>> working) {
            this.profiles = profiles;
            this.ours = ours;
            this.keepers = keepers;
            this.stored = working;
        }

        private Outcome run(List<CompanyArea> found) {
            stored.forEach((key, players) -> AreaRef.parse(key).ifPresent(area -> {
                var ref = providers.call(area.provider(), provider -> provider.canonical(area.ref()), area.ref());
                working.computeIfAbsent(new AreaRef(area.provider(), ref).toString(), any -> new HashSet<>())
                        .addAll(players);
            }));
            Map<CompanyArea, String> named = new LinkedHashMap<>();
            for (var area : found) {
                if (!providers.available(area.provider()) || Bukkit.getWorld(area.world()) == null) continue;
                named.put(
                        area, providers.call(area.provider(), provider -> provider.canonical(area.ref()), area.ref()));
            }
            named.entrySet().stream()
                    .sorted(Comparator.comparing((Map.Entry<CompanyArea, String> entry) ->
                                    !entry.getKey().ref().equals(entry.getValue()))
                            .thenComparing(entry -> entry.getKey().id()))
                    .forEach(entry -> visit(entry.getKey(), entry.getValue()));
            return new Outcome(changes, kept, lost, touched);
        }

        private void visit(CompanyArea area, String ref) {
            var id = area.provider();
            var profile = profiles.getOrDefault(area.company(), Optional.empty());
            var mine = ours.getOrDefault(area.id(), Set.of());
            var exists = providers.call(id, provider -> provider.exists(area.ref()), true);
            if (area.rentEnded() && exists) {
                handBack(area);
                return;
            }
            if (area.rentEnded() || profile.isEmpty() || !exists || lostLink(area, profile.get())) {
                if (!lostLongEnough(area)) {
                    kept.put(new AreaRef(id, ref).toString(), area.company());
                    return;
                }
                if (exists && area.fromServer()) {
                    handBack(area);
                } else {
                    drop(area, mine);
                }
                return;
            }
            var key = new AreaRef(id, ref).toString();
            var other = stayed.get(key);
            if (other != null) {
                merge(area, mine, other);
                return;
            }
            stayed.put(key, area);
            if (!ref.equals(area.ref())) {
                touched.add(area.company());
                changes.add(store -> rename(store, area, ref));
            }
            kept.put(key, area.company());
            if (area.fromServer()) followOwner(area, ref, profile.get());
            if (!providers.call(id, provider -> provider.capabilities().contains(AreaCapability.TRUST_LIST), false)) {
                return;
            }
            var builders = providers.call(id, provider -> provider.builders(ref), Set.<UUID>of());
            Set<UUID> removable = new HashSet<>(mine);
            if (area.fromServer()) {
                removable.addAll(builders);
                removable.removeAll(working.getOrDefault(new AreaRef(id, ref).toString(), Set.of()));
            }
            var plan = plan(
                    wanted(profile.get()),
                    providers.call(id, provider -> provider.owners(ref), Set.of()),
                    builders,
                    removable);
            for (var player : plan.add()) {
                if (providers.call(id, provider -> provider.addBuilder(ref, player), false)) {
                    changes.add(store -> store.addBuilder(area.id(), player));
                }
            }
            for (var player : plan.remove()) {
                if (providers.call(id, provider -> provider.removeBuilder(ref, player), false)) {
                    changes.add(store -> store.removeBuilder(area.id(), player));
                }
            }
        }

        /**
         * Drops an area that now names the same one as an area already kept. Its builders stay builders and are noted
         * on the area kept when both belong to one company; otherwise they are taken off, and a member of the company
         * kept is made builder again by the next check.
         */
        private void merge(CompanyArea area, Set<UUID> mine, CompanyArea other) {
            if (other.company() != area.company()) {
                drop(area, mine);
                return;
            }
            touched.add(area.company());
            changes.add(store -> {
                for (var player : mine) store.addBuilder(other.id(), player);
                store.remove(area.id());
            });
        }

        /**
         * Notes that an area looks lost and tells whether it has looked so for at least {@value #CHECK_SECONDS}
         * seconds.
         */
        private boolean lostLongEnough(CompanyArea area) {
            var since = suspected.getOrDefault(area.id(), now);
            if (!since.plusSeconds(CHECK_SECONDS).isAfter(now)) return true;
            lost.put(area.id(), since);
            return false;
        }

        /**
         * Makes the owner of the company the only owner of a bought or rented area, as after the company got a new
         * owner.
         */
        private void followOwner(CompanyArea area, String ref, CompanyProfile company) {
            var owner = company.members().stream()
                    .filter(member -> member.hasPermission(CompanyPermission.OWNER))
                    .map(ICompanyMember::uuid)
                    .findFirst();
            if (owner.isEmpty()) return;
            var id = area.provider();
            if (providers
                    .call(id, provider -> provider.owners(ref), Set.<UUID>of())
                    .equals(Set.of(owner.get()))) return;
            if (!providers.call(id, provider -> provider.capabilities().contains(AreaCapability.TRANSFER), false)) {
                return;
            }
            if (!providers.call(id, provider -> provider.transferTo(ref, owner.get()), false)) {
                logger.warning("Could not give company area " + area.id() + " to the owner of its company");
            }
        }

        private void handBack(CompanyArea area) {
            if (!release(area, keepers.get(area.id()))) return;
            touched.add(area.company());
            changes.add(store -> store.remove(area.id()));
        }

        private void drop(CompanyArea area, Set<UUID> mine) {
            for (var player : mine) {
                providers.call(area.provider(), provider -> provider.removeBuilder(area.ref(), player), false);
            }
            touched.add(area.company());
            changes.add(store -> store.remove(area.id()));
        }
    }

    private void rename(AAreaData store, CompanyArea area, String ref) {
        if (store.rename(area.id(), ref)) return;
        logger.warning("Could not store company area " + area.id() + " as " + ref + ", another area has that name");
    }

    /**
     * @return whether a linked area lost the member it is tied to: they left the company or no longer own the area
     */
    private boolean lostLink(CompanyArea area, CompanyProfile company) {
        if (area.source() != AreaSource.LINKED) return false;
        var linker = area.linkedBy();
        if (linker == null || company.member(linker).isEmpty()) return true;
        return !providers.call(area.provider(), provider -> provider.isOwner(linker, area.ref()), true);
    }

    private static Set<UUID> wanted(CompanyProfile company) {
        Set<UUID> wanted = new HashSet<>();
        for (var member : company.members()) {
            if (member.hasPermission(CompanyPermission.BUILD)) wanted.add(member.uuid());
        }
        return wanted;
    }

    /**
     * Checks soon after a member joined.
     *
     * @param event the joining
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(CompanyJoinEvent event) {
        checkSoon();
    }

    /**
     * Checks soon after a member left.
     *
     * @param event the leaving
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeave(CompanyLeaveEvent event) {
        checkSoon();
    }

    /**
     * Checks soon after a member was kicked.
     *
     * @param event the kick
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(CompanyKickEvent event) {
        checkSoon();
    }

    /**
     * Checks soon after a member got other permissions.
     *
     * @param event the change
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPermission(CompanyMemberPermissionEvent event) {
        checkSoon();
    }

    /**
     * Checks soon after a company got a new owner.
     *
     * @param event the transfer
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransfer(CompanyOwnerTransferEvent event) {
        checkSoon();
    }

    /**
     * Checks soon after a company was disbanded, which gives its areas back.
     *
     * @param event the disbanding
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisband(CompanyDisbandEvent event) {
        checkSoon();
    }

    /**
     * A change to write to the database after the round on the main thread.
     */
    @FunctionalInterface
    private interface Change {
        void write(AAreaData areas);
    }

    private record Outcome(
            List<Change> changes, Map<String, Integer> kept, Map<Integer, Instant> suspected, Set<Integer> touched) {}
}
