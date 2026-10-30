/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows the members of a company working on a build job the blocks of one layer that are missing or wrong, as small
 * glowing copies of the blocks the blueprint asks for. Only the player who asked sees them. A player sees one layer of
 * one job at a time, at most {@value #MAX_GHOSTS} blocks of it; they are removed when the player asks for another
 * layer, turns them off or leaves, and when the plugin stops. Runs on the main thread.
 */
public final class BuildGhosts implements Listener {
    /**
     * The most ghost blocks shown at once to one player.
     */
    public static final int MAX_GHOSTS = 1024;

    private final Plugin plugin;
    private final Map<UUID, Shown> shown = new ConcurrentHashMap<>();

    /**
     * @param plugin the plugin owning the ghost blocks
     */
    public BuildGhosts(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * What a layer of a build job still lacks.
     *
     * @param y     the height of the layer
     * @param asked how many blocks the blueprint asks for in it
     * @param open  how many of them are missing or wrong
     */
    public record Layer(int y, int asked, int open) {}

    /**
     * @param area      a build job's area
     * @param blueprint its blueprint
     * @param types     reads the world
     * @param y         the height of a layer
     * @return what the layer still lacks
     */
    public static Layer layer(JobArea area, Blueprint blueprint, BuildCheck.Types types, int y) {
        var asked = 0;
        var open = 0;
        for (var x = area.minX(); x <= area.maxX(); x++) {
            for (var z = area.minZ(); z <= area.maxZ(); z++) {
                var wanted = blueprint.at(area, x, y, z);
                if (wanted == null) continue;
                asked++;
                if (types.at(x, y, z) != wanted) open++;
            }
        }
        return new Layer(y, asked, open);
    }

    /**
     * @param area      a build job's area
     * @param blueprint its blueprint
     * @param types     reads the world
     * @return the lowest layer that still lacks a block, the lowest layer when none does
     */
    public static int firstOpen(JobArea area, Blueprint blueprint, BuildCheck.Types types) {
        for (var y = area.minY(); y <= area.maxY(); y++) {
            if (layer(area, blueprint, types, y).open() > 0) return y;
        }
        return area.minY();
    }

    /**
     * Shows a player the missing and wrong blocks of a layer, replacing what they saw before.
     *
     * @param player    the player
     * @param job       a build job of this server
     * @param blueprint its blueprint
     * @param y         the height of the layer, kept within the area
     * @return what the layer still lacks, empty when the job's world is not loaded
     */
    public Optional<Layer> show(Player player, Job job, Blueprint blueprint, int y) {
        hide(player);
        var area = job.area();
        var world = plugin.getServer().getWorld(area.world());
        if (world == null) return Optional.empty();
        var layerY = Math.clamp(y, area.minY(), area.maxY());
        var types = BuildCheck.of(world);
        List<Entity> ghosts = new ArrayList<>();
        for (var x = area.minX(); x <= area.maxX() && ghosts.size() < MAX_GHOSTS; x++) {
            for (var z = area.minZ(); z <= area.maxZ() && ghosts.size() < MAX_GHOSTS; z++) {
                var wanted = blueprint.at(area, x, layerY, z);
                if (wanted == null || types.at(x, layerY, z) == wanted) continue;
                var data = wanted.createBlockData();
                var ghost = world.spawn(world.getBlockAt(x, layerY, z).getLocation(), BlockDisplay.class, display -> {
                    display.setBlock(data);
                    display.setVisibleByDefault(false);
                    display.setPersistent(false);
                    display.setGlowing(true);
                    display.setTransformation(new Transformation(
                            new Vector3f(0.2f, 0.2f, 0.2f),
                            new AxisAngle4f(),
                            new Vector3f(0.6f, 0.6f, 0.6f),
                            new AxisAngle4f()));
                });
                player.showEntity(plugin, ghost);
                ghosts.add(ghost);
            }
        }
        shown.put(player.getUniqueId(), new Shown(job.id(), layerY, ghosts));
        return Optional.of(layer(area, blueprint, types, layerY));
    }

    /**
     * @param player a player
     * @param job    id of a job
     * @return the layer of the job the player sees, empty when they see none of it
     */
    public Optional<Integer> layerOf(Player player, int job) {
        var current = shown.get(player.getUniqueId());
        return current == null || current.job() != job ? Optional.empty() : Optional.of(current.y());
    }

    /**
     * Removes the ghost blocks a player sees.
     *
     * @param player the player
     */
    public void hide(Player player) {
        var current = shown.remove(player.getUniqueId());
        if (current != null) current.ghosts().forEach(Entity::remove);
    }

    /**
     * Removes every ghost block.
     */
    public void stop() {
        shown.values().forEach(current -> current.ghosts().forEach(Entity::remove));
        shown.clear();
    }

    /**
     * @param event the player leaving
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hide(event.getPlayer());
    }

    private record Shown(int job, int y, List<Entity> ghosts) {}
}
