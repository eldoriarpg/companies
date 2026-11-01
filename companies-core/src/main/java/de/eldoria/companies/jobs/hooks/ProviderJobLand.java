/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs.hooks;

import de.eldoria.companies.areas.AreaProviders;
import de.eldoria.companies.areas.AreaRef;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.AreaProvider;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Jobs on the land of every area provider that declares {@link AreaCapability#JOB_LAND}, such as PlotSquared plots
 * and claims of claim plugins, in the order the providers are asked.
 * <p>
 * The area of a job must lie wholly in one area of one provider, owned by the job's owner; every column is checked. A
 * column in another area, in land the provider guards without an area ({@link
 * de.eldoria.companies.components.area.AreaProvider#protects}), or partly outside the area refuses it. While a company
 * works on the job, its members are made builders of the area. Only members who were neither owners nor builders are
 * added, and only those are taken off again, so the area's own builders stay as they were. Being a builder reaches
 * the whole area, so {@link #reaches} tells which blocks the members may only change inside the job's area. A member
 * who already builds there through the company's area is not noted as the job's; should the company take that away
 * while the job runs, the member builds again from the job's next grant, a few seconds later. The providers are read on
 * every call, so a provider registered or dropped later counts at once, and a provider switched off in
 * {@code areas.yml} carries no jobs.
 */
public final class ProviderJobLand implements JobLandHook {
    private final AreaProviders providers;

    /**
     * @param providers the area providers
     */
    public ProviderJobLand(AreaProviders providers) {
        this.providers = providers;
    }

    @Override
    public String plugin() {
        return "area providers";
    }

    @Override
    public Claim claim(OfflinePlayer owner, JobArea area) {
        var world = Bukkit.getWorld(area.world());
        if (world == null) return new Claim.None();
        Claim.Owned owned = null;
        for (var id : landProviders()) {
            var claim = claim(id, owner.getUniqueId(), world, area);
            if (claim instanceof Claim.Foreign) return claim;
            if (owned == null && claim instanceof Claim.Owned found) owned = found;
        }
        return owned == null ? new Claim.None() : owned;
    }

    private Claim claim(String id, UUID owner, World world, JobArea area) {
        return providers.call(id, provider -> claim(provider, id, owner, world, area), new Claim.Foreign(null));
    }

    /**
     * Checks every column of a job's area against one provider, resolved once for all of them.
     */
    private static Claim claim(AreaProvider provider, String id, UUID owner, World world, JobArea area) {
        String found = null;
        var outside = false;
        var location = new Location(world, 0, area.minY(), 0);
        for (var x = area.minX(); x <= area.maxX(); x++) {
            for (var z = area.minZ(); z <= area.maxZ(); z++) {
                location.setX(x);
                location.setZ(z);
                var here = first(provider.areasAt(location));
                if (here == null) {
                    if (provider.protects(location)) return new Claim.Foreign(null);
                    outside = true;
                    continue;
                }
                if (found == null) found = here;
                else if (!found.equals(here)) return new Claim.Foreign(here);
            }
        }
        if (found == null) return new Claim.None();
        if (outside || !provider.isOwner(owner, found)) return new Claim.Foreign(found);
        return new Claim.Owned(JobLand.AREA, new AreaRef(id, found).toString());
    }

    @Override
    public Set<UUID> grant(Job job, Collection<UUID> members, Set<UUID> granted) {
        var area = area(job);
        if (area.isEmpty()) return Set.of();
        var id = area.get().provider();
        var ref = area.get().ref();
        if (!providers.available(id) || !providers.call(id, provider -> provider.exists(ref), false)) return granted;
        Set<UUID> given = new HashSet<>();
        for (var member : members) {
            if (granted.contains(member)) {
                given.add(member);
                continue;
            }
            if (mayBuild(id, ref, member)) continue;
            if (providers.call(id, provider -> provider.addBuilder(ref, member), false)) given.add(member);
        }
        for (var player : granted) {
            if (!given.contains(player)) providers.call(id, provider -> provider.removeBuilder(ref, player), false);
        }
        return given;
    }

    @Override
    public Set<UUID> planned(Job job, Collection<UUID> members, Set<UUID> granted) {
        var area = area(job);
        if (area.isEmpty()) return Set.of();
        var id = area.get().provider();
        var ref = area.get().ref();
        if (!providers.available(id)) return granted;
        Set<UUID> planned = new HashSet<>(granted);
        for (var member : members) {
            if (!mayBuild(id, ref, member)) planned.add(member);
        }
        return planned;
    }

    @Override
    public boolean revoke(Job job, Set<UUID> granted) {
        var area = area(job);
        if (area.isEmpty()) return true;
        var id = area.get().provider();
        var ref = area.get().ref();
        if (!providers.available(id) || Bukkit.getWorld(job.area().world()) == null) return false;
        if (!providers.call(id, provider -> provider.exists(ref), true)) return true;
        var done = true;
        for (var player : granted) {
            done &= providers.call(id, provider -> provider.removeBuilder(ref, player), false);
        }
        return done;
    }

    @Override
    public boolean owns(Job job) {
        var world = Bukkit.getWorld(job.area().world());
        if (world == null) return true;
        return switch (job.land()) {
            case AREA -> {
                var area = area(job);
                if (area.isEmpty() || !providers.available(area.get().provider())) yield true;
                var ref = area.get().ref();
                yield providers.call(
                        area.get().provider(),
                        provider -> provider.exists(ref) && provider.isOwner(job.owner(), ref),
                        true);
            }
            case UNOWNED -> {
                var centre = centre(world, job.area());
                for (var id : landProviders()) {
                    if (providers.call(id, provider -> !provider.areasAt(centre).isEmpty(), false)) yield false;
                }
                yield true;
            }
            case REGION, NONE -> true;
        };
    }

    @Override
    public boolean reaches(Job job, String world, int x, int y, int z) {
        if (!job.area().world().equals(world)) return false;
        var area = area(job);
        var bukkit = Bukkit.getWorld(world);
        if (area.isEmpty() || bukkit == null) return false;
        var ref = area.get().ref();
        var location = new Location(bukkit, x, y, z);
        return providers.call(
                area.get().provider(), provider -> provider.areasAt(location).contains(ref), false);
    }

    @Override
    public Optional<JobArea> ownArea(Player player) {
        var location = player.getLocation();
        var uuid = player.getUniqueId();
        for (var id : landProviders()) {
            var found = providers.call(
                    id,
                    provider -> {
                        var ref = first(provider.areasAt(location));
                        if (ref == null || !provider.isOwner(uuid, ref)) return Optional.<JobArea>empty();
                        return provider.bounds(ref).map(ProviderJobLand::box);
                    },
                    Optional.<JobArea>empty());
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    /**
     * @return the providers whose land jobs may lie on, in the order they are asked
     */
    private List<String> landProviders() {
        return providers.providers().stream()
                .filter(id -> providers.call(
                        id,
                        provider -> provider.capabilities()
                                .containsAll(Set.of(AreaCapability.JOB_LAND, AreaCapability.TRUST_LIST)),
                        false))
                .toList();
    }

    /**
     * The area of a job on the land of a provider, by the name its provider gives it now, as after plots were merged.
     */
    private Optional<AreaRef> area(Job job) {
        var landRef = job.landRef();
        if (job.land() != JobLand.AREA || landRef == null) return Optional.empty();
        var stored = AreaRef.parse(landRef);
        if (stored.isEmpty()) return Optional.empty();
        var id = stored.get().provider();
        var ref = stored.get().ref();
        return Optional.of(new AreaRef(id, providers.call(id, provider -> provider.canonical(ref), ref)));
    }

    private boolean mayBuild(String id, String ref, UUID player) {
        return providers.call(
                id,
                provider ->
                        provider.isOwner(player, ref) || provider.builders(ref).contains(player),
                true);
    }

    private static JobArea box(de.eldoria.companies.components.area.AreaBounds bounds) {
        var boxes = bounds.boxes();
        var first = boxes.getFirst();
        int minX = first.minX(), minY = first.minY(), minZ = first.minZ();
        int maxX = first.maxX(), maxY = first.maxY(), maxZ = first.maxZ();
        for (var box : boxes) {
            minX = Math.min(minX, box.minX());
            minY = Math.min(minY, box.minY());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY());
            maxZ = Math.max(maxZ, box.maxZ());
        }
        return new JobArea(bounds.world(), minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Location centre(World world, JobArea area) {
        return new Location(world, (area.minX() + area.maxX()) / 2, area.minY(), (area.minZ() + area.maxZ()) / 2);
    }

    private static @Nullable String first(List<String> refs) {
        return refs.isEmpty() ? null : refs.getFirst();
    }
}
