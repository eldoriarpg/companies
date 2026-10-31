/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import de.eldoria.companies.components.area.AreaBounds;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.AreaProvider;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * WorldGuard regions as company areas. A region is named {@code world/region-id}. The areas at a location are the
 * regions there, the highest priority first and equal priorities by id, leaving out the global region and the regions
 * Companies makes for jobs. The owners of a region own the area; its members are the builders.
 */
public final class WorldGuardAreas implements AreaProvider {
    private static final String JOB_REGION = "companies-job-";

    @Override
    public String id() {
        return "worldguard";
    }

    @Override
    public Set<AreaCapability> capabilities() {
        return EnumSet.of(AreaCapability.LINK_OWNED, AreaCapability.TRANSFER, AreaCapability.TRUST_LIST);
    }

    @Override
    public List<String> areasAt(Location location) {
        var world = location.getWorld();
        if (world == null) return List.of();
        var regions = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .createQuery()
                .getApplicableRegions(BukkitAdapter.adapt(location));
        return regions.getRegions().stream()
                .filter(region -> !region.getId().equals(ProtectedRegion.GLOBAL_REGION))
                .filter(region -> !region.getId().startsWith(JOB_REGION))
                .sorted(Comparator.comparingInt(ProtectedRegion::getPriority)
                        .reversed()
                        .thenComparing(ProtectedRegion::getId))
                .map(region -> world.getName() + "/" + region.getId())
                .toList();
    }

    @Override
    public boolean exists(String ref) {
        return region(ref) != null;
    }

    @Override
    public Set<UUID> owners(String ref) {
        var region = region(ref);
        return region == null ? Set.of() : Set.copyOf(region.getOwners().getUniqueIds());
    }

    @Override
    public Optional<AreaBounds> bounds(String ref) {
        var region = region(ref);
        if (region == null) return Optional.empty();
        var min = region.getMinimumPoint();
        var max = region.getMaximumPoint();
        return Optional.of(new AreaBounds(
                world(ref), List.of(new AreaBounds.Box(min.x(), min.y(), min.z(), max.x(), max.y(), max.z()))));
    }

    @Override
    public String name(String ref) {
        var slash = ref.indexOf('/');
        return slash < 0 ? ref : ref.substring(slash + 1);
    }

    @Override
    public Set<UUID> builders(String ref) {
        var region = region(ref);
        return region == null ? Set.of() : Set.copyOf(region.getMembers().getUniqueIds());
    }

    @Override
    public boolean addBuilder(String ref, UUID player) {
        var region = region(ref);
        if (region == null) return false;
        region.getMembers().addPlayer(player);
        return true;
    }

    @Override
    public boolean removeBuilder(String ref, UUID player) {
        var region = region(ref);
        if (region == null) return false;
        region.getMembers().removePlayer(player);
        return true;
    }

    @Override
    public boolean transferTo(String ref, UUID player) {
        var region = region(ref);
        if (region == null) return false;
        region.getOwners().removeAll();
        region.getOwners().addPlayer(player);
        return true;
    }

    @Override
    public boolean releasesToNobody() {
        return true;
    }

    @Override
    public boolean release(String ref, @Nullable UUID keeper) {
        var region = region(ref);
        if (region == null) return false;
        region.getOwners().removeAll();
        region.getMembers().removeAll();
        if (keeper != null) region.getOwners().addPlayer(keeper);
        return true;
    }

    private static String world(String ref) {
        var slash = ref.indexOf('/');
        return slash < 0 ? "" : ref.substring(0, slash);
    }

    private static @Nullable ProtectedRegion region(String ref) {
        var slash = ref.indexOf('/');
        if (slash <= 0) return null;
        var manager = manager(ref.substring(0, slash));
        return manager == null ? null : manager.getRegion(ref.substring(slash + 1));
    }

    private static @Nullable RegionManager manager(String world) {
        var found = Bukkit.getWorld(world);
        if (found == null) return null;
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(found));
    }
}
