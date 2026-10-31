/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.towny;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.PlotGroup;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import de.eldoria.companies.components.area.AreaBounds;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.ClaimAreaProvider;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Towny plots of residents as company areas and job land. An area is a personal plot: a town block a resident owns,
 * named {@code world;x;z} by its world and cell, or the whole plot group it belongs to when the group's resident owns
 * every block of it, named {@code world/group/id}; a dissolved group is gone and its blocks have to be linked anew.
 * Town plots without a resident are the town's land: they are guarded, never areas, so jobs there are refused. The
 * resident owns the area; the plot's trusted residents are the builders, and Towny's cached rights of a player are
 * reset whenever Companies trusts or untrusts them. A player Towny does not know yet, as one who never joined, can not
 * be trusted until they did. Plots stay with residents of their town and Towny sells them itself, so they are linked,
 * never bought or rented through Companies.
 */
public final class TownyAreas extends ClaimAreaProvider<TownyAreas.Plot> {
    private static final String GROUP = "/group/";

    /**
     * A personal plot: one town block, or the plot group it belongs to.
     *
     * @param world the name of the world
     * @param block a town block of the plot
     * @param group the plot group, null for a single town block
     */
    public record Plot(
            String world, TownBlock block, @Nullable PlotGroup group) {}

    @Override
    public String id() {
        return "towny";
    }

    @Override
    public Set<AreaCapability> capabilities() {
        return EnumSet.of(AreaCapability.LINK_OWNED, AreaCapability.TRUST_LIST, AreaCapability.JOB_LAND);
    }

    @Override
    public boolean protects(Location location) {
        var block = TownyAPI.getInstance().getTownBlock(location);
        return block != null && block.getResidentOrNull() == null;
    }

    @Override
    protected Optional<Plot> claimAt(Location location) {
        var world = location.getWorld();
        var block = TownyAPI.getInstance().getTownBlock(location);
        if (world == null || block == null || block.getResidentOrNull() == null) return Optional.empty();
        return Optional.of(plot(world.getName(), block));
    }

    @Override
    protected Optional<Plot> claim(String ref) {
        var group = ref.indexOf(GROUP);
        if (group > 0) {
            UUID id;
            try {
                id = UUID.fromString(ref.substring(group + GROUP.length()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            var found = TownyUniverse.getInstance().getGroup(id);
            if (found == null || !personal(found)) return Optional.empty();
            return Optional.of(new Plot(
                    ref.substring(0, group), found.getTownBlocks().iterator().next(), found));
        }
        var parts = ref.split(";");
        if (parts.length != 3) return Optional.empty();
        int x;
        int z;
        try {
            x = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        var block = new WorldCoord(parts[0], x, z).getTownBlockOrNull();
        if (block == null || block.getResidentOrNull() == null) return Optional.empty();
        return Optional.of(plot(parts[0], block));
    }

    @Override
    protected String ref(Plot plot) {
        var group = plot.group();
        if (group != null) return plot.world() + GROUP + group.getUUID();
        return plot.world() + ";" + plot.block().getX() + ";" + plot.block().getZ();
    }

    @Override
    protected Set<UUID> claimOwners(Plot plot) {
        var group = plot.group();
        var owner = group != null ? group.getResident() : plot.block().getResidentOrNull();
        return owner == null ? Set.of() : Set.of(owner.getUUID());
    }

    @Override
    protected String world(Plot plot) {
        return plot.world();
    }

    @Override
    protected List<AreaBounds.Box> boxes(Plot plot) {
        var world = Bukkit.getWorld(plot.world());
        if (world == null) return List.of();
        var size = TownySettings.getTownBlockSize();
        List<AreaBounds.Box> boxes = new ArrayList<>();
        for (var block : blocks(plot)) {
            var x = block.getX() * size;
            var z = block.getZ() * size;
            boxes.add(new AreaBounds.Box(
                    x, world.getMinHeight(), z, x + size - 1, world.getMaxHeight() - 1, z + size - 1));
        }
        return boxes;
    }

    @Override
    protected String claimName(Plot plot) {
        var town = plot.block().getTownOrNull();
        var group = plot.group();
        var where = group != null
                ? group.getName()
                : plot.block().getX() + ", " + plot.block().getZ();
        return town == null ? where : town.getName() + " " + where;
    }

    @Override
    protected Set<UUID> trusted(Plot plot) {
        Set<UUID> players = new HashSet<>();
        for (var resident : trustedResidents(plot)) players.add(resident.getUUID());
        return players;
    }

    @Override
    protected boolean trust(Plot plot, UUID player) {
        var resident = TownyAPI.getInstance().getResident(player);
        if (resident == null) return false;
        var group = plot.group();
        if (group != null) {
            group.addTrustedResident(resident);
            group.save();
        } else {
            plot.block().addTrustedResident(resident);
            plot.block().save();
        }
        Towny.getPlugin().deleteCache(resident);
        return trusted(plot).contains(player);
    }

    @Override
    protected boolean untrust(Plot plot, UUID player) {
        var resident = TownyAPI.getInstance().getResident(player);
        if (resident == null) return true;
        var group = plot.group();
        if (group != null) {
            group.removeTrustedResident(resident);
            group.save();
        } else {
            plot.block().removeTrustedResident(resident);
            plot.block().save();
        }
        Towny.getPlugin().deleteCache(resident);
        return !trusted(plot).contains(player);
    }

    private static Set<Resident> trustedResidents(Plot plot) {
        var group = plot.group();
        return group != null ? group.getTrustedResidents() : plot.block().getTrustedResidents();
    }

    private static List<TownBlock> blocks(Plot plot) {
        var group = plot.group();
        return group != null ? List.copyOf(group.getTownBlocks()) : List.of(plot.block());
    }

    private static Plot plot(String world, TownBlock block) {
        if (block.hasPlotObjectGroup()) {
            var group = block.getPlotObjectGroup();
            if (group != null && personal(group)) return new Plot(world, block, group);
        }
        return new Plot(world, block, null);
    }

    /**
     * Whether a plot group is one personal plot: its resident owns every block of it.
     */
    private static boolean personal(PlotGroup group) {
        var owner = group.getResident();
        var blocks = group.getTownBlocks();
        if (owner == null || blocks.isEmpty()) return false;
        for (var block : blocks) {
            if (!owner.equals(block.getResidentOrNull())) return false;
        }
        return true;
    }
}
