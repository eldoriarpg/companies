/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.wrapper.job.JobArea;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * What a build job asks for: the block type of every position of its area, from a schematic placed and rotated by the
 * owner. Positions the schematic leaves as air, liquid or unbreakable ask for nothing, so terrain and air inside a
 * building never count. Only
 * block types are compared, not their states, so a stair facing another way or a fence that connects differently
 * still matches.
 */
public final class Blueprint {
    private final int width;
    private final int height;
    private final int length;
    private final List<Material> palette;
    private final int[] cells;
    private final int blocks;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;

    private Blueprint(int width, int height, int length, List<Material> palette, int[] cells, int[] offset) {
        this.offsetX = offset[0];
        this.offsetY = offset[1];
        this.offsetZ = offset[2];
        this.width = width;
        this.height = height;
        this.length = length;
        this.palette = List.copyOf(palette);
        this.cells = cells;
        var count = 0;
        for (var cell : cells) if (cell > 0) count++;
        this.blocks = count;
    }

    /**
     * A block of a schematic, relative to the point the owner stood on.
     *
     * @param x    x offset
     * @param y    y offset
     * @param z    z offset
     * @param type the block type
     */
    public record Cell(int x, int y, int z, Material type) {}

    /**
     * @param cells the blocks of a schematic, air left out
     * @return the blueprint, its corner at the lowest offsets
     * @throws IllegalArgumentException when there are no blocks
     */
    public static Blueprint of(List<Cell> cells) {
        if (cells.isEmpty()) throw new IllegalArgumentException("no blocks");
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var cell : cells) {
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxY = Math.max(maxY, cell.y());
            maxZ = Math.max(maxZ, cell.z());
        }
        int width = maxX - minX + 1, height = maxY - minY + 1, length = maxZ - minZ + 1;
        List<Material> palette = new ArrayList<>();
        Map<Material, Integer> index = new HashMap<>();
        var grid = new int[Math.multiplyExact(Math.multiplyExact(width, height), length)];
        for (var cell : cells) {
            if (!wanted(cell.type())) continue;
            var id = index.computeIfAbsent(cell.type(), type -> {
                palette.add(type);
                return palette.size();
            });
            grid[((cell.y() - minY) * length + (cell.z() - minZ)) * width + (cell.x() - minX)] = id;
        }
        return new Blueprint(width, height, length, palette, grid, new int[] {minX, minY, minZ});
    }

    /**
     * @param type a block type of a schematic
     * @return whether a build job asks for it: not for air, liquids or blocks nobody can break or place, which count
     *     as "anything goes"
     */
    static boolean wanted(Material type) {
        return !type.isAir()
                && type != Material.WATER
                && type != Material.LAVA
                && type != Material.BUBBLE_COLUMN
                && type.getHardness() >= 0;
    }

    /**
     * @param world name of the world
     * @param x     x of the point the owner stood on
     * @param y     y of that point
     * @param z     z of that point
     * @return the area the blueprint covers when placed there
     */
    public JobArea area(String world, int x, int y, int z) {
        var minX = x + offsetX;
        var minY = y + offsetY;
        var minZ = z + offsetZ;
        return new JobArea(world, minX, minY, minZ, minX + width - 1, minY + height - 1, minZ + length - 1);
    }

    /**
     * @param area the area the blueprint was placed on
     * @param x    x of a block
     * @param y    y of a block
     * @param z    z of a block
     * @return the type the blueprint asks for there, null for none or outside the area
     */
    public @Nullable Material at(JobArea area, int x, int y, int z) {
        int dx = x - area.minX(), dy = y - area.minY(), dz = z - area.minZ();
        if (dx < 0 || dy < 0 || dz < 0 || dx >= width || dy >= height || dz >= length) return null;
        var id = cells[(dy * length + dz) * width + dx];
        return id == 0 ? null : palette.get(id - 1);
    }

    /**
     * @return how many positions ask for a block
     */
    public int blocks() {
        return blocks;
    }

    /**
     * @return the size along x, y and z
     */
    public int[] size() {
        return new int[] {width, height, length};
    }

    /**
     * @return the blueprint as compact text, to store it with its job; the offsets are dropped, as a stored blueprint
     *     is read against the job's area
     */
    public String encode() {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(width);
            out.writeInt(height);
            out.writeInt(length);
            out.writeInt(palette.size());
            for (var type : palette) out.writeUTF(type.name());
            for (var cell : cells) out.writeShort(cell);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    /**
     * @param text a blueprint as {@link #encode()} wrote it
     * @return the blueprint; a block type this server does not know asks for nothing
     */
    public static Blueprint decode(String text) {
        try (var in = new DataInputStream(
                new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text))))) {
            int width = in.readInt(), height = in.readInt(), length = in.readInt();
            var size = in.readInt();
            List<Material> palette = new ArrayList<>();
            for (var i = 0; i < size; i++) {
                var type = Material.matchMaterial(in.readUTF());
                palette.add(type == null ? Material.AIR : type);
            }
            var cells = new int[width * height * length];
            for (var i = 0; i < cells.length; i++) {
                var id = in.readShort();
                cells[i] = id > 0 && !palette.get(id - 1).isAir() ? id : 0;
            }
            return new Blueprint(width, height, length, palette, cells, new int[3]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
