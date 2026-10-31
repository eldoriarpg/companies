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
import de.eldoria.companies.components.area.AreaProvider;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * PlotSquared plots as company areas. A plot is named {@code plot-area|x;z} by its plot area and the id of a plot;
 * merged plots are one area, named by their base plot, and {@link #canonical(String)} turns the id of any of them into
 * the base plot's, also after plots were merged or unmerged. Only claimed plots are areas. The owners of the plot own
 * the area; its trusted players are the builders. A plot is never unclaimed: given back, it goes to a keeper.
 */
public final class PlotSquaredAreas implements AreaProvider {
    @Override
    public String id() {
        return "plotsquared";
    }

    @Override
    public Set<AreaCapability> capabilities() {
        return EnumSet.of(AreaCapability.LINK_OWNED, AreaCapability.TRANSFER, AreaCapability.TRUST_LIST);
    }

    @Override
    public List<String> areasAt(Location location) {
        var world = location.getWorld();
        if (world == null) return List.of();
        var at = com.plotsquared.core.location.Location.at(
                world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (!at.isPlotArea()) return List.of();
        var plot = at.getOwnedPlot();
        if (plot == null) return List.of();
        return List.of(ref(plot.getBasePlot(false)));
    }

    @Override
    public String canonical(String ref) {
        var plot = plot(ref);
        return plot == null ? ref : ref(plot);
    }

    @Override
    public boolean exists(String ref) {
        return plot(ref) != null;
    }

    @Override
    public Set<UUID> owners(String ref) {
        var plot = plot(ref);
        return plot == null ? Set.of() : Set.copyOf(plot.getOwners());
    }

    @Override
    public Optional<AreaBounds> bounds(String ref) {
        var plot = plot(ref);
        if (plot == null) return Optional.empty();
        List<AreaBounds.Box> boxes = new ArrayList<>();
        for (var region : plot.getRegions()) {
            var min = region.getMinimumPoint();
            var max = region.getMaximumPoint();
            boxes.add(new AreaBounds.Box(min.x(), min.y(), min.z(), max.x(), max.y(), max.z()));
        }
        return boxes.isEmpty() ? Optional.empty() : Optional.of(new AreaBounds(plot.getWorldName(), boxes));
    }

    @Override
    public String name(String ref) {
        var bar = ref.lastIndexOf('|');
        return bar < 0 ? ref : ref.substring(bar + 1) + " (" + ref.substring(0, bar) + ")";
    }

    @Override
    public Set<UUID> builders(String ref) {
        var plot = plot(ref);
        return plot == null ? Set.of() : Set.copyOf(plot.getTrusted());
    }

    @Override
    public boolean addBuilder(String ref, UUID player) {
        var plot = plot(ref);
        if (plot == null) return false;
        if (!plot.getTrusted().contains(player)) plot.addTrusted(player);
        return true;
    }

    @Override
    public boolean removeBuilder(String ref, UUID player) {
        var plot = plot(ref);
        if (plot == null) return false;
        plot.removeTrusted(player);
        return true;
    }

    @Override
    public boolean transferTo(String ref, UUID player) {
        var plot = plot(ref);
        if (plot == null) return false;
        plot.setOwner(player);
        return true;
    }

    @Override
    public boolean release(String ref, @Nullable UUID keeper) {
        var plot = plot(ref);
        if (plot == null || keeper == null) return false;
        for (var trusted : Set.copyOf(plot.getTrusted())) plot.removeTrusted(trusted);
        for (var member : Set.copyOf(plot.getMembers())) plot.removeMember(member);
        plot.setOwner(keeper);
        return true;
    }

    private static @Nullable Plot plot(String ref) {
        var bar = ref.lastIndexOf('|');
        if (bar <= 0) return null;
        var area = PlotSquared.get().getPlotAreaManager().getPlotAreaByString(ref.substring(0, bar));
        var id = PlotId.fromStringOrNull(ref.substring(bar + 1));
        if (area == null || id == null) return null;
        var plot = area.getPlot(id);
        if (plot == null) return null;
        var base = plot.getBasePlot(false);
        return base.hasOwner() ? base : null;
    }

    private static String ref(Plot plot) {
        return plot.getArea() + "|" + plot.getId();
    }
}
