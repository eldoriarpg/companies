/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.worldedit;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.jobs.Blueprint;
import de.eldoria.companies.jobs.hooks.SelectionHook;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the box a player selected with WorldEdit or FastAsyncWorldEdit. Other shapes are not a box and are refused.
 */
public final class WorldEditSelections implements SelectionHook {
    /**
     * The largest schematic file read, so a huge file never fills the memory before its size can be checked.
     */
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

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

    @Override
    public Optional<Blueprint> schematic(Path file, int rotation, int maxVolume) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) return Optional.empty();
        var format = ClipboardFormats.findByFile(file.toFile());
        if (format == null) return Optional.empty();
        Clipboard clipboard;
        try (var reader = format.getReader(Files.newInputStream(file))) {
            clipboard = reader.read();
        }
        if (clipboard.getRegion().getVolume() > maxVolume) return Optional.empty();
        var turn = new AffineTransform().rotateY(-rotation);
        var origin = clipboard.getOrigin();
        List<Blueprint.Cell> cells = new ArrayList<>();
        for (BlockVector3 point : clipboard.getRegion()) {
            var type = clipboard.getBlock(point).getBlockType();
            if (type.getMaterial().isAir()) continue;
            var turned = turn.apply(point.subtract(origin).toVector3());
            cells.add(new Blueprint.Cell(
                    (int) Math.round(turned.x()),
                    (int) Math.round(turned.y()),
                    (int) Math.round(turned.z()),
                    BukkitAdapter.adapt(type)));
        }
        return cells.isEmpty() ? Optional.empty() : Optional.of(Blueprint.of(cells));
    }
}
