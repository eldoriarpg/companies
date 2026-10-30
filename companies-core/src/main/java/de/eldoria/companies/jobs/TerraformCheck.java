/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.job.JobTarget;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Checks a terraform job: every column of its area must be solid up to the target height and empty above it. Above the
 * target a block is done as for a dig job: air, water, lava or unbreakable. At or below it a block is done when it is
 * solid; the top layer, at the target height, must be the target's surface block when it names one. Unbreakable blocks
 * count as done everywhere, as nobody could change them.
 */
public final class TerraformCheck {
    private static final Set<Material> UNBUILDABLE = EnumSet.of(
            Material.SPAWNER,
            Material.TRIAL_SPAWNER,
            Material.VAULT,
            Material.BUDDING_AMETHYST,
            Material.REINFORCED_DEEPSLATE,
            Material.FARMLAND,
            Material.DIRT_PATH,
            Material.FROSTED_ICE);

    private TerraformCheck() {}

    /**
     * Reads the blocks of a world against a target. Call it on the main thread with the area's chunks loaded, see
     * {@link JobProgress}.
     *
     * @param world  the world
     * @param target the shape the job asks for
     * @return the reader, whose {@link DigCheck.Blocks#cleared} says whether a block is as the target asks
     */
    public static DigCheck.Blocks of(World world, JobTarget target) {
        var surface = surface(target);
        return (x, y, z) -> done(world.getBlockAt(x, y, z), y, target.y(), surface);
    }

    /**
     * @param block   a block of the area
     * @param y       its height
     * @param targetY the target height
     * @param surface the block the top layer must be made of, null for any solid block
     * @return whether the block is as the target asks
     */
    static boolean done(Block block, int y, int targetY, @Nullable Material surface) {
        var type = block.getType();
        if (type.getHardness() < 0 && !block.isEmpty()) return true;
        if (y > targetY) return block.isEmpty() || block.isLiquid();
        if (!type.isSolid()) return false;
        return y < targetY || surface == null || type == surface;
    }

    /**
     * @param target a target
     * @return the material of its surface, null for any solid block or a name no block has
     */
    static @Nullable Material surface(JobTarget target) {
        var name = target.surface();
        return name == null ? null : block(name);
    }

    /**
     * @param name a block name as a player typed it, such as {@code grass_block}
     * @return the solid block it names that players can place and keep in survival, null when it names none; blocks
     *     nobody can break, blocks that are no item, and blocks no player gets or that change by themselves, such as
     *     copper that is not waxed or concrete powder, are refused, so no job asks for a surface nobody can build or keep
     */
    public static @Nullable Material block(String name) {
        var material = Material.matchMaterial(name);
        if (material == null || !material.isBlock() || !material.isSolid() || !material.isItem()) return null;
        if (material.getHardness() < 0 || UNBUILDABLE.contains(material)) return null;
        var key = material.name();
        if (key.startsWith("INFESTED_") || key.endsWith("_CONCRETE_POWDER")) return null;
        var oxidizes = key.contains("COPPER") && !key.startsWith("WAXED_") && !key.startsWith("OXIDIZED_");
        return oxidizes ? null : material;
    }

    /**
     * @param block a block placed in the area
     * @param y     its height
     * @param target the shape the job asks for
     * @return whether the block fills its position as the target asks: solid at or below the target height, and the
     *     surface block at the target height when the target names one
     */
    static boolean fills(Material block, int y, JobTarget target) {
        if (y > target.y() || !block.isSolid()) return false;
        var surface = surface(target);
        return y < target.y() || surface == null || block == surface;
    }
}
