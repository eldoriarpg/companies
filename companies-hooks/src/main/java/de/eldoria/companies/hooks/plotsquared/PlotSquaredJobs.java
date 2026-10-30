/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.plotsquared;

import com.plotsquared.core.location.Location;
import com.plotsquared.core.plot.Plot;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.jobs.hooks.Claim;
import de.eldoria.companies.jobs.hooks.JobLandHook;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Jobs on PlotSquared plots.
 * <p>
 * The area of a job must lie wholly on one plot the owner owns, merged plots counting as one; every column is checked,
 * and a road or a plot of someone else refuses it. While a company works on the job, its members are trusted on the
 * plot. Only members who were not trusted and do not own the plot are added, and only those are removed again, so the
 * plot's own trust stays as it was. Members who leave the company are removed while the job runs; all are removed when
 * it ends. Trust reaches the whole plot, so {@link #reaches} tells which blocks the members may only change inside the
 * job's area.
 */
public final class PlotSquaredJobs implements JobLandHook {
    @Override
    public String plugin() {
        return "PlotSquared";
    }

    @Override
    public Claim claim(Player owner, JobArea area) {
        Plot found = null;
        var inPlotWorld = false;
        for (var x = area.minX(); x <= area.maxX(); x++) {
            for (var z = area.minZ(); z <= area.maxZ(); z++) {
                var location = Location.at(area.world(), x, area.minY(), z);
                if (!location.isPlotArea()) continue;
                inPlotWorld = true;
                var plot = location.getOwnedPlot();
                if (plot == null) return new Claim.Foreign(null);
                var base = plot.getBasePlot(false);
                if (found == null) found = base;
                else if (!found.equals(base))
                    return new Claim.Foreign(base.getId().toString());
            }
        }
        if (!inPlotWorld || found == null) return new Claim.None();
        if (!found.isOwner(owner.getUniqueId()))
            return new Claim.Foreign(found.getId().toString());
        return new Claim.Owned(JobLand.PLOT, found.getId().toString());
    }

    @Override
    public Set<UUID> grant(Job job, Collection<UUID> members, Set<UUID> granted) {
        if (job.land() != JobLand.PLOT) return Set.of();
        var plot = plot(job);
        if (plot == null) return granted;
        Set<UUID> given = new HashSet<>();
        for (var member : members) {
            if (granted.contains(member)) {
                given.add(member);
                continue;
            }
            if (plot.isOwner(member) || plot.getTrusted().contains(member)) continue;
            plot.addTrusted(member);
            given.add(member);
        }
        for (var player : granted) {
            if (!given.contains(player)) plot.removeTrusted(player);
        }
        return given;
    }

    @Override
    public Set<UUID> planned(Job job, Collection<UUID> members, Set<UUID> granted) {
        if (job.land() != JobLand.PLOT) return Set.of();
        var plot = plot(job);
        if (plot == null) return granted;
        Set<UUID> planned = new HashSet<>(granted);
        for (var member : members) {
            if (!plot.isOwner(member) && !plot.getTrusted().contains(member)) planned.add(member);
        }
        return planned;
    }

    @Override
    public boolean revoke(Job job, Set<UUID> granted) {
        if (job.land() != JobLand.PLOT) return true;
        var plot = plot(job);
        if (plot == null) return true;
        for (var player : granted) plot.removeTrusted(player);
        return true;
    }

    @Override
    public boolean owns(Job job) {
        var area = job.area();
        var center = Location.at(
                area.world(), (area.minX() + area.maxX()) / 2, area.minY(), (area.minZ() + area.maxZ()) / 2);
        if (!center.isPlotArea()) return true;
        return switch (job.land()) {
            case PLOT -> {
                var plot = plot(job);
                yield plot != null && plot.isOwner(job.owner());
            }
            case UNOWNED -> center.getOwnedPlot() == null;
            case REGION -> true;
        };
    }

    @Override
    public boolean reaches(Job job, String world, int x, int y, int z) {
        if (job.land() != JobLand.PLOT || !job.area().world().equals(world)) return false;
        var plot = plot(job);
        if (plot == null) return false;
        var at = Location.at(world, x, y, z).getOwnedPlot();
        return at != null && plot.equals(at.getBasePlot(false));
    }

    @Override
    public Optional<JobArea> ownArea(Player player) {
        var at = player.getLocation();
        var location = Location.at(at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
        var plot = location.getOwnedPlot();
        if (plot == null || !plot.isOwner(player.getUniqueId())) return Optional.empty();
        var region = plot.getBasePlot(false).getLargestRegion();
        if (region == null) return Optional.empty();
        var min = region.getMinimumPoint();
        var max = region.getMaximumPoint();
        return Optional.of(new JobArea(location.getWorldName(), min.x(), min.y(), min.z(), max.x(), max.y(), max.z()));
    }

    private static @Nullable Plot plot(Job job) {
        var area = job.area();
        var location = Location.at(
                area.world(), (area.minX() + area.maxX()) / 2, area.minY(), (area.minZ() + area.maxZ()) / 2);
        var plot = location.getOwnedPlot();
        return plot == null ? null : plot.getBasePlot(false);
    }
}
