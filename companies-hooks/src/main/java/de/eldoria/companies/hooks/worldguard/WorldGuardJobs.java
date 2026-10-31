/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.jobs.hooks.Claim;
import de.eldoria.companies.jobs.hooks.JobLandHook;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Jobs in WorldGuard regions.
 * <p>
 * The area of a job must lie wholly in one region the owner owns; any region the owner does not own, and any other
 * job's region, refuses it. Every column of the area is checked, so a region that is not a box still has to cover all
 * of it. While a company works on the job, a region {@code companies-job-<id>} on its area, above every region there,
 * lists the company's members and the job's owner as members, so they may change it; other members of the owner's
 * region can not build in it while it exists. The region also marks a job on unowned land. It is removed when the job
 * ends; while the world is not loaded that is tried again later.
 */
public final class WorldGuardJobs implements JobLandHook {
    private static final String JOB_REGION = "companies-job-";

    @Override
    public String plugin() {
        return "WorldGuard";
    }

    @Override
    public Claim claim(OfflinePlayer owner, JobArea area) {
        var manager = manager(area.world());
        if (manager == null) return new Claim.None();
        var regions = manager.getApplicableRegions(probe(area));
        if (regions.size() == 0) return new Claim.None();
        var local = owner instanceof Player online
                ? WorldGuardPlugin.inst().wrapPlayer(online)
                : WorldGuardPlugin.inst().wrapOfflinePlayer(owner);
        ProtectedRegion holding = null;
        for (var region : regions) {
            if (region.getId().startsWith(JOB_REGION)) return new Claim.Foreign(region.getId());
            if (!region.isOwner(local)) return new Claim.Foreign(region.getId());
            if (holds(region, area, true) && (holding == null || region.getPriority() > holding.getPriority())) {
                holding = region;
            }
        }
        if (holding == null) return new Claim.Foreign(regions.iterator().next().getId());
        return new Claim.Owned(JobLand.REGION, holding.getId());
    }

    @Override
    public Set<UUID> grant(Job job, Collection<UUID> members, Set<UUID> granted) {
        if (job.land() == JobLand.AREA) return Set.of();
        var manager = manager(job.area().world());
        if (manager == null) return Set.of();
        var region = manager.getRegion(job.regionName());
        if (region == null) {
            region = probe(job.area(), job.regionName());
            var priority = 0;
            for (var other : manager.getApplicableRegions(region)) {
                priority = Math.max(priority, other.getPriority() + 1);
            }
            region.setPriority(priority);
            manager.addRegion(region);
        }
        var domain = new DefaultDomain();
        members.forEach(domain::addPlayer);
        domain.addPlayer(job.owner());
        region.setMembers(domain);
        return Set.of();
    }

    @Override
    public boolean revoke(Job job, Set<UUID> granted) {
        var manager = manager(job.area().world());
        if (manager == null) return false;
        manager.removeRegion(job.regionName());
        return true;
    }

    @Override
    public boolean owns(Job job) {
        var manager = manager(job.area().world());
        if (manager == null) return true;
        return switch (job.land()) {
            case AREA, NONE -> true;
            case REGION -> {
                var ref = job.landRef();
                var region = ref == null ? null : manager.getRegion(ref);
                yield region != null && owned(region, job.owner()) && holds(region, job.area(), false);
            }
            case UNOWNED -> {
                for (var region : manager.getApplicableRegions(probe(job.area()))) {
                    if (!region.getId().equals(job.regionName())) yield false;
                }
                yield true;
            }
        };
    }

    private static @Nullable RegionManager manager(String world) {
        var found = Bukkit.getWorld(world);
        if (found == null) return null;
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(found));
    }

    private static ProtectedCuboidRegion probe(JobArea area) {
        return probe(area, "companies-probe");
    }

    private static ProtectedCuboidRegion probe(JobArea area, String id) {
        return new ProtectedCuboidRegion(
                id,
                BlockVector3.at(area.minX(), area.minY(), area.minZ()),
                BlockVector3.at(area.maxX(), area.maxY(), area.maxZ()));
    }

    /**
     * @param region   a region
     * @param area     an area
     * @param thorough whether to check every column of a region that is not a box, as when posting; later checks only
     *                 look at the corners
     * @return whether the region holds the whole area
     */
    private static boolean holds(ProtectedRegion region, JobArea area, boolean thorough) {
        if (region instanceof ProtectedCuboidRegion) {
            var min = region.getMinimumPoint();
            var max = region.getMaximumPoint();
            return min.x() <= area.minX()
                    && min.y() <= area.minY()
                    && min.z() <= area.minZ()
                    && max.x() >= area.maxX()
                    && max.y() >= area.maxY()
                    && max.z() >= area.maxZ();
        }
        var stepX = thorough ? 1 : Math.max(1, area.maxX() - area.minX());
        var stepZ = thorough ? 1 : Math.max(1, area.maxZ() - area.minZ());
        for (var x = area.minX(); x <= area.maxX(); x += stepX) {
            for (var z = area.minZ(); z <= area.maxZ(); z += stepZ) {
                if (!region.contains(x, area.minY(), z) || !region.contains(x, area.maxY(), z)) return false;
            }
        }
        return true;
    }

    /**
     * Whether a player still owns a region: as an owner of it or of one of its parents, or as a member of a group that
     * owns one of them. When the player's groups can not be read, such as without a permission plugin that knows
     * offline players, a region owned by groups is taken as still owned.
     *
     * @param region the region
     * @param owner  the player
     * @return false only when the player is no owner anymore
     */
    private static boolean owned(ProtectedRegion region, UUID owner) {
        String[] groups = null;
        for (var current = region; current != null; current = current.getParent()) {
            var owners = current.getOwners();
            if (owners.contains(owner)) return true;
            if (owners.getGroups().isEmpty()) continue;
            if (groups == null) {
                groups = WorldGuardPlugin.inst()
                        .wrapOfflinePlayer(Bukkit.getOfflinePlayer(owner))
                        .getGroups();
            }
            if (groups.length == 0) return true;
            for (var group : groups) {
                if (owners.getGroups().contains(group.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }
}
