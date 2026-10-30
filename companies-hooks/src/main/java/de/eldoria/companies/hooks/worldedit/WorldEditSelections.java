/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.worldedit;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.CuboidRegion;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.jobs.hooks.SelectionHook;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Reads the box a player selected with WorldEdit or FastAsyncWorldEdit. Other shapes are not a box and are refused.
 */
public final class WorldEditSelections implements SelectionHook {
    @Override
    public Optional<JobArea> selection(Player player) {
        var session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        var world = session.getSelectionWorld();
        if (world == null) return Optional.empty();
        try {
            var region = session.getSelection(world);
            if (!(region instanceof CuboidRegion)) return Optional.empty();
            var min = region.getMinimumPoint();
            var max = region.getMaximumPoint();
            return Optional.of(new JobArea(world.getName(), min.x(), min.y(), min.z(), max.x(), max.y(), max.z()));
        } catch (IncompleteRegionException e) {
            return Optional.empty();
        }
    }
}
