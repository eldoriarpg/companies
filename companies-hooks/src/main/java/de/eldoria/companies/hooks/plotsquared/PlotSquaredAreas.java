/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.plotsquared;

import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotId;
import de.eldoria.companies.components.area.AreaBounds;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.ClaimAreaProvider;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * PlotSquared plots as company areas and job land. A plot is named {@code plot-area|x;z} by its plot area and the id of
 * a plot; merged plots are one area, named by their base plot, also after plots were merged or unmerged. Only claimed
 * plots are areas; roads and unclaimed plots of a plot world are guarded land without an area. The owners of the plot
 * own the area; its trusted players are the builders. A plot is never unclaimed: given back, it goes to a keeper.
 */
public final class PlotSquaredAreas extends ClaimAreaProvider<Plot> {
    @Override
    public String id() {
        return "plotsquared";
    }

    @Override
    public Set<AreaCapability> capabilities() {
        return EnumSet.of(
                AreaCapability.LINK_OWNED, AreaCapability.TRANSFER, AreaCapability.TRUST_LIST, AreaCapability.JOB_LAND);
    }

    @Override
    public boolean protects(Location location) {
        var at = plotLocation(location);
        return at != null && at.isPlotArea() && at.getOwnedPlot() == null;
    }

    @Override
    protected Optional<Plot> claimAt(Location location) {
        var at = plotLocation(location);
        if (at == null || !at.isPlotArea()) return Optional.empty();
        return Optional.ofNullable(at.getOwnedPlot());
    }

    @Override
    protected Optional<Plot> claim(String ref) {
        var bar = ref.lastIndexOf('|');
        if (bar <= 0) return Optional.empty();
        var area = PlotSquared.get().getPlotAreaManager().getPlotAreaByString(ref.substring(0, bar));
        var id = PlotId.fromStringOrNull(ref.substring(bar + 1));
        if (area == null || id == null) return Optional.empty();
        var plot = area.getPlot(id);
        if (plot == null) return Optional.empty();
        var base = plot.getBasePlot(false);
        return base.hasOwner() ? Optional.of(base) : Optional.empty();
    }

    @Override
    protected Plot topClaim(Plot claim) {
        return claim.getBasePlot(false);
    }

    @Override
    protected String ref(Plot claim) {
        return claim.getArea() + "|" + claim.getId();
    }

    @Override
    protected Set<UUID> claimOwners(Plot claim) {
        return Set.copyOf(claim.getOwners());
    }

    @Override
    protected String world(Plot claim) {
        return claim.getWorldName();
    }

    @Override
    protected List<AreaBounds.Box> boxes(Plot claim) {
        List<AreaBounds.Box> boxes = new ArrayList<>();
        for (var region : claim.getRegions()) {
            var min = region.getMinimumPoint();
            var max = region.getMaximumPoint();
            boxes.add(new AreaBounds.Box(min.x(), min.y(), min.z(), max.x(), max.y(), max.z()));
        }
        return boxes;
    }

    @Override
    protected String claimName(Plot claim) {
        return claim.getId() + " (" + claim.getArea() + ")";
    }

    @Override
    protected Set<UUID> trusted(Plot claim) {
        return Set.copyOf(claim.getTrusted());
    }

    @Override
    protected boolean trust(Plot claim, UUID player) {
        claim.addTrusted(player);
        return true;
    }

    @Override
    protected boolean untrust(Plot claim, UUID player) {
        claim.removeTrusted(player);
        return true;
    }

    @Override
    protected boolean transfer(Plot claim, UUID player) {
        claim.setOwner(player);
        return true;
    }

    @Override
    protected boolean giveBack(Plot claim, @Nullable UUID keeper) {
        if (keeper == null) return false;
        for (var trusted : Set.copyOf(claim.getTrusted())) claim.removeTrusted(trusted);
        for (var member : Set.copyOf(claim.getMembers())) claim.removeMember(member);
        claim.setOwner(keeper);
        return true;
    }

    private static com.plotsquared.core.location.@Nullable Location plotLocation(Location location) {
        var world = location.getWorld();
        if (world == null) return null;
        return com.plotsquared.core.location.Location.at(
                world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
