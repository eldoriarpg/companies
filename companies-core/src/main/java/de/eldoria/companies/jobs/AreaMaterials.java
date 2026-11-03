/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobTarget;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The blocks an area job digs out and fills in, counted from the area as it stands. A dig job digs every block; a
 * terraform job digs what lies above its height and the top layer that is not its surface block, and fills what is not
 * solid at or below its height, with the surface block in the top layer and the fill block below. Air, water, lava and
 * blocks nobody can break are never dug; blocks nobody can break count as filled.
 *
 * @param dug   the blocks dug out, by type
 * @param fill  the blocks filled in, by type
 * @param cells the positions filled in with their block, as offsets from the lowest corner of the area
 */
public record AreaMaterials(Map<Material, Integer> dug, Map<Material, Integer> fill, List<Blueprint.Cell> cells) {
    /**
     * The block a terraform job is filled with below its surface when the owner names none.
     */
    public static final Material DEFAULT_FILL = Material.DIRT;

    /**
     * Keeps the counts unchangeable.
     */
    public AreaMaterials {
        dug = Map.copyOf(dug);
        fill = Map.copyOf(fill);
        cells = List.copyOf(cells);
    }

    /**
     * @param kind      the kind of job
     * @param area      its area
     * @param target    the shape of a terraform job, null for other kinds
     * @param fill      the block a terraform job is filled with below its surface
     * @param types     reads the blocks of the area
     * @return what the job digs and fills
     */
    public static AreaMaterials of(
            JobKind kind, JobArea area, @Nullable JobTarget target, Material fill, BuildCheck.Types types) {
        Map<Material, Integer> dug = new TreeMap<>();
        Map<Material, Integer> filled = new TreeMap<>();
        List<Blueprint.Cell> cells = new ArrayList<>();
        var surface = target == null ? null : TerraformCheck.surface(target);
        for (var y = area.minY(); y <= area.maxY(); y++) {
            for (var x = area.minX(); x <= area.maxX(); x++) {
                for (var z = area.minZ(); z <= area.maxZ(); z++) {
                    var type = types.at(x, y, z);
                    if (digs(kind, target, surface, type, y)) dug.merge(type, 1, Integer::sum);
                    if (kind != JobKind.TERRAFORM || target == null || y > target.y()) continue;
                    var wanted = wanted(type, y, target.y(), surface, fill);
                    if (wanted == null) continue;
                    filled.merge(wanted, 1, Integer::sum);
                    cells.add(new Blueprint.Cell(x - area.minX(), y - area.minY(), z - area.minZ(), wanted));
                }
            }
        }
        return new AreaMaterials(dug, filled, cells);
    }

    private static boolean digs(
            JobKind kind, @Nullable JobTarget target, @Nullable Material surface, Material type, int y) {
        if (!breakable(type)) return false;
        if (kind == JobKind.DIG) return true;
        return kind == JobKind.TERRAFORM
                && target != null
                && (y > target.y() || (y == target.y() && surface != null && type.isSolid() && type != surface));
    }

    private static boolean breakable(Material type) {
        return !type.isAir() && type != Material.WATER && type != Material.LAVA && type.getHardness() >= 0;
    }

    /**
     * @return the block a terraform job fills a position with, null when it is solid as asked already
     */
    private static @Nullable Material wanted(
            Material type, int y, int targetY, @Nullable Material surface, Material fill) {
        if (type.getHardness() < 0 && !type.isAir()) return null;
        var top = y == targetY;
        if (type.isSolid() && (!top || surface == null || type == surface)) return null;
        return top && surface != null ? surface : fill;
    }

    /**
     * @param area the job's area
     * @return the blueprint of the fill, spanning the whole area, null when nothing is filled in
     */
    public @Nullable Blueprint blueprint(JobArea area) {
        if (cells.isEmpty()) return null;
        var corners = List.of(
                new int[] {0, 0, 0},
                new int[] {area.maxX() - area.minX(), area.maxY() - area.minY(), area.maxZ() - area.minZ()});
        return Blueprint.of(cells, corners);
    }
}
