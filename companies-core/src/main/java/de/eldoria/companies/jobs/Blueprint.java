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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * What a build job asks for: the block of every position of its area, with its block data, from a schematic placed and
 * rotated by the owner. Positions the schematic leaves as air, liquid or unbreakable ask for nothing, so terrain and air
 * inside a building never count. A block matches when its type is the one asked for and its orientation states
 * ({@link #FIXED}) are as the blueprint fixes them; states its neighbours decide, like the connections of a fence or
 * the shape of a stair, are kept for showing the blueprint but never compared.
 */
public final class Blueprint {
    /**
     * The states a blueprint fixes: how a block is turned. A slab's {@code type} counts as well, see
     * {@link Want#fixed()}.
     */
    public static final Set<String> FIXED =
            Set.of("facing", "half", "axis", "rotation", "hinge", "face", "attachment", "orientation");

    private static final Set<String> SLAB_TYPES = Set.of("top", "bottom", "double");
    private static final int FORMAT = -2;
    private static final int MAX_PALETTE = 0xFFFF;

    private final int width;
    private final int height;
    private final int length;
    private final List<Want> palette;
    private final int[] cells;
    private final int blocks;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;

    private Blueprint(int width, int height, int length, List<Want> palette, int[] cells, int[] offset) {
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
     * @param data the block data as text, as {@code BlockData#getAsString()} writes it
     */
    public record Cell(int x, int y, int z, Material type, String data) {
        /**
         * A block of the type alone, with no states fixed.
         *
         * @param x    x offset
         * @param y    y offset
         * @param z    z offset
         * @param type the block type
         */
        public Cell(int x, int y, int z, Material type) {
            this(x, y, z, type, key(type));
        }
    }

    /**
     * The block a position asks for.
     *
     * @param type  the block type
     * @param data  the block data as text, with every state the schematic had
     * @param fixed the states the block must have to match: its orientation, and a slab's type
     */
    public record Want(Material type, String data, Map<String, String> fixed) {
        /**
         * @param type the block type
         * @param data the block data as text
         */
        public Want(Material type, String data) {
            this(type, data, fixedOf(data));
        }

        /**
         * Keeps the fixed states unchangeable.
         */
        public Want {
            fixed = Map.copyOf(fixed);
        }

        /**
         * @param placedType the type of a placed block
         * @param placed     its block data as text
         * @return whether it is this block: the same type and every fixed state the same
         */
        public boolean matches(Material placedType, String placed) {
            if (placedType != type) return false;
            if (fixed.isEmpty()) return true;
            var states = states(placed);
            for (var state : fixed.entrySet()) {
                if (!state.getValue().equals(states.get(state.getKey()))) return false;
            }
            return true;
        }

        /**
         * @param placedType the type of a placed block
         * @param placed     its block data as text
         * @return the placed block turned as this block asks, null when it is another type, already matches, is one half
         *     of a block of two (a door, a tall plant, a bed), a chest joined to another, or a slab that is or should be
         *     double, which would change how many items the block holds
         */
        public @Nullable String turn(Material placedType, String placed) {
            if (placedType != type || matches(placedType, placed)) return null;
            var states = states(placed);
            if (states.containsKey("part")
                    || "upper".equals(states.get("half"))
                    || "lower".equals(states.get("half"))
                    || "left".equals(states.get("type"))
                    || "right".equals(states.get("type"))
                    || "double".equals(states.get("type"))
                    || "double".equals(fixed.get("type"))) {
                return null;
            }
            fixed.forEach((name, value) -> {
                if (states.containsKey(name)) states.put(name, value);
            });
            return withStates(placed, states);
        }
    }

    private static Map<String, String> fixedOf(String data) {
        Map<String, String> fixed = new HashMap<>();
        states(data).forEach((name, value) -> {
            if (FIXED.contains(name) || (name.equals("type") && SLAB_TYPES.contains(value))) fixed.put(name, value);
        });
        return fixed;
    }

    /**
     * @param type a block type
     * @return its namespaced key as block data text with no states
     */
    static String key(Material type) {
        return "minecraft:" + type.name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param data block data as text
     * @return its states by name, in their order
     */
    static Map<String, String> states(String data) {
        Map<String, String> states = new LinkedHashMap<>();
        var open = data.indexOf('[');
        if (open < 0 || !data.endsWith("]")) return states;
        for (var pair : data.substring(open + 1, data.length() - 1).split(",")) {
            var eq = pair.indexOf('=');
            if (eq > 0) states.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return states;
    }

    private static String withStates(String data, Map<String, String> states) {
        var open = data.indexOf('[');
        var base = open < 0 ? data : data.substring(0, open);
        if (states.isEmpty()) return base;
        var text = new StringBuilder(base).append('[');
        states.forEach(
                (name, value) -> text.append(name).append('=').append(value).append(','));
        text.setCharAt(text.length() - 1, ']');
        return text.toString();
    }

    /**
     * @param cells the blocks of a schematic, air left out
     * @return the blueprint, its corner at the lowest offsets
     * @throws IllegalArgumentException when there are no blocks or more than 65535 different ones
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
        List<Want> palette = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        var grid = new int[Math.multiplyExact(Math.multiplyExact(width, height), length)];
        for (var cell : cells) {
            if (!wanted(cell.type())) continue;
            var id = index.computeIfAbsent(cell.data(), data -> {
                palette.add(new Want(cell.type(), data));
                return palette.size();
            });
            if (id > MAX_PALETTE) throw new IllegalArgumentException("more than " + MAX_PALETTE + " different blocks");
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
        var want = want(area, x, y, z);
        return want == null ? null : want.type();
    }

    /**
     * @param area the area the blueprint was placed on
     * @param x    x of a block
     * @param y    y of a block
     * @param z    z of a block
     * @return the block the blueprint asks for there, null for none or outside the area
     */
    public @Nullable Want want(JobArea area, int x, int y, int z) {
        int dx = x - area.minX(), dy = y - area.minY(), dz = z - area.minZ();
        if (dx < 0 || dy < 0 || dz < 0 || dx >= width || dy >= height || dz >= length) return null;
        var id = cells[(dy * length + dz) * width + dx];
        return id == 0 ? null : palette.get(id - 1);
    }

    /**
     * @param other another object
     * @return whether it is a blueprint of the same size asking for the same blocks at the same positions
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Blueprint blueprint
                && width == blueprint.width
                && height == blueprint.height
                && length == blueprint.length
                && cells().equals(blueprint.cells());
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, length, blocks);
    }

    /**
     * @return how many positions ask for each block
     */
    public Map<Want, Integer> wants() {
        Map<Want, Integer> wants = new LinkedHashMap<>();
        for (var cell : cells) {
            if (cell > 0) wants.merge(palette.get(cell - 1), 1, Integer::sum);
        }
        return wants;
    }

    /**
     * @return how many blocks of each type the blueprint asks for
     */
    public Map<Material, Integer> counts() {
        Map<Material, Integer> counts = new LinkedHashMap<>();
        for (var cell : cells) {
            if (cell > 0) counts.merge(palette.get(cell - 1).type(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * @return every position that asks for a block, at offsets from the lowest corner of the area
     */
    public List<Cell> cells() {
        List<Cell> found = new ArrayList<>();
        for (var y = 0; y < height; y++) {
            for (var z = 0; z < length; z++) {
                for (var x = 0; x < width; x++) {
                    var id = cells[(y * length + z) * width + x];
                    if (id == 0) continue;
                    var want = palette.get(id - 1);
                    found.add(new Cell(x, y, z, want.type(), want.data()));
                }
            }
        }
        return found;
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
            out.writeInt(FORMAT);
            out.writeInt(width);
            out.writeInt(height);
            out.writeInt(length);
            out.writeInt(palette.size());
            for (var want : palette) out.writeUTF(want.data());
            for (var cell : cells) out.writeShort(cell);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    /**
     * @param text a blueprint as {@link #encode()} wrote it, or one stored before blueprints kept block data, which
     *     asks for types alone
     * @return the blueprint; a block type this server does not know asks for nothing
     */
    public static Blueprint decode(String text) {
        try (var in = new DataInputStream(
                new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text))))) {
            var first = in.readInt();
            var withData = first == FORMAT;
            int width = withData ? in.readInt() : first, height = in.readInt(), length = in.readInt();
            var size = in.readInt();
            List<Want> palette = new ArrayList<>();
            for (var i = 0; i < size; i++) palette.add(want(in.readUTF()));
            var cells = new int[width * height * length];
            for (var i = 0; i < cells.length; i++) {
                var id = in.readUnsignedShort();
                cells[i] = id > 0 && !palette.get(id - 1).type().isAir() ? id : 0;
            }
            return new Blueprint(width, height, length, palette, cells, new int[3]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Want want(String stored) {
        var open = stored.indexOf('[');
        var type = Material.matchMaterial(open < 0 ? stored : stored.substring(0, open));
        if (type == null) return new Want(Material.AIR, key(Material.AIR));
        return new Want(type, stored.contains(":") ? stored : key(type));
    }
}
