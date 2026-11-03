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
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * What a build job asks for: the block of every position of its area, with its block data, from a schematic placed and
 * rotated by the owner. Positions the schematic leaves as air, liquid or unbreakable ask for nothing, so terrain and air
 * inside a building never count, unless they lie below the ground where the blueprint is placed: those positions must be
 * dug out ({@link #dug}), so a schematic may reach into the ground. A block matches when its type is the one asked for
 * and its orientation states
 * ({@link #FIXED}) are as the blueprint fixes them; states its neighbours decide, like the connections of a fence or
 * the shape of a stair, are kept for showing the blueprint but never compared.
 * <p>
 * The water and lava sources of the schematic are kept apart as {@link #fluids()}: they ask for nothing either, but
 * a job whose owner supplies the materials stocks and places them.
 */
public final class Blueprint {
    /**
     * The states a blueprint fixes: how a block is turned. A slab's {@code type} counts as well, see
     * {@link Want#fixed()}.
     */
    public static final Set<String> FIXED =
            Set.of("facing", "half", "axis", "rotation", "hinge", "face", "attachment", "orientation");

    private static final Set<String> SLAB_TYPES = Set.of("top", "bottom", "double");
    private static final int FORMAT = -3;
    private static final int FORMAT_WITHOUT_FLUIDS = -2;
    private static final int MAX_PALETTE = 0xFFFF;
    private static final int UNPLACED_AIR = -1;

    /**
     * What a position that must be dug out asks for.
     */
    public static final Want DIG = new Want(Material.AIR, "minecraft:air");

    private final int width;
    private final int height;
    private final int length;
    private final List<Want> palette;
    private final int[] cells;
    private final int blocks;
    private final int digs;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;
    private final List<Fluid> fluids;

    private Blueprint(
            int width, int height, int length, List<Want> palette, int[] cells, int[] offset, List<Fluid> fluids) {
        this.offsetX = offset[0];
        this.offsetY = offset[1];
        this.offsetZ = offset[2];
        this.width = width;
        this.height = height;
        this.length = length;
        this.palette = List.copyOf(palette);
        this.cells = cells;
        this.fluids = List.copyOf(fluids);
        var count = 0;
        var dig = 0;
        for (var cell : cells) {
            if (cell <= 0) continue;
            if (this.palette.get(cell - 1).type().isAir()) {
                dig++;
            } else {
                count++;
            }
        }
        this.blocks = count;
        this.digs = dig;
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
     * A water or lava source of a schematic.
     *
     * @param x    x offset from the lowest corner of the blueprint
     * @param y    y offset from the lowest corner
     * @param z    z offset from the lowest corner
     * @param type {@link Material#WATER} or {@link Material#LAVA}
     */
    public record Fluid(int x, int y, int z, Material type) {}

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
     * @param cells the blocks of a schematic, air left out; its water and lava ask for nothing and their sources are
     *              kept as {@link #fluids()}
     * @return the blueprint, its corner at the lowest offsets
     * @throws IllegalArgumentException when there are no blocks or more than 65535 different ones
     */
    public static Blueprint of(List<Cell> cells) {
        return of(cells, List.of());
    }

    /**
     * @param cells the blocks of a schematic, air left out; its water and lava count as air and their sources are
     *              kept as {@link #fluids()}
     * @param air   the positions the schematic leaves as air, as x, y and z offsets; dug out where they lie below the
     *              ground once the blueprint is placed, see {@link #dug}
     * @return the blueprint, its corner at the lowest offsets
     * @throws IllegalArgumentException when no block is asked for or there are more than 65535 different ones
     */
    public static Blueprint of(List<Cell> cells, List<int[]> air) {
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
        for (var position : air) {
            minX = Math.min(minX, position[0]);
            minY = Math.min(minY, position[1]);
            minZ = Math.min(minZ, position[2]);
            maxX = Math.max(maxX, position[0]);
            maxY = Math.max(maxY, position[1]);
            maxZ = Math.max(maxZ, position[2]);
        }
        int width = maxX - minX + 1, height = maxY - minY + 1, length = maxZ - minZ + 1;
        List<Want> palette = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        var grid = new int[Math.multiplyExact(Math.multiplyExact(width, height), length)];
        for (var position : air) {
            grid[((position[1] - minY) * length + (position[2] - minZ)) * width + (position[0] - minX)] = UNPLACED_AIR;
        }
        List<Fluid> fluids = new ArrayList<>();
        for (var cell : cells) {
            if (!wanted(cell.type())) {
                if (cell.type() == Material.WATER
                        || cell.type() == Material.LAVA
                        || cell.type() == Material.BUBBLE_COLUMN) {
                    grid[((cell.y() - minY) * length + (cell.z() - minZ)) * width + (cell.x() - minX)] = UNPLACED_AIR;
                }
                if (source(cell)) {
                    var type = cell.type() == Material.LAVA ? Material.LAVA : Material.WATER;
                    fluids.add(new Fluid(cell.x() - minX, cell.y() - minY, cell.z() - minZ, type));
                }
                continue;
            }
            var id = index.computeIfAbsent(cell.data(), data -> {
                palette.add(new Want(cell.type(), data));
                return palette.size();
            });
            if (id > MAX_PALETTE) throw new IllegalArgumentException("more than " + MAX_PALETTE + " different blocks");
            grid[((cell.y() - minY) * length + (cell.z() - minZ)) * width + (cell.x() - minX)] = id;
        }
        if (palette.isEmpty()) throw new IllegalArgumentException("no blocks");
        return new Blueprint(width, height, length, palette, grid, new int[] {minX, minY, minZ}, fluids);
    }

    /**
     * @param cell a block of a schematic
     * @return whether it is a water or lava source; a bubble column stands on a water source
     */
    private static boolean source(Cell cell) {
        if (cell.type() == Material.BUBBLE_COLUMN) return true;
        if (cell.type() != Material.WATER && cell.type() != Material.LAVA) return false;
        var level = states(cell.data()).get("level");
        return level == null || "0".equals(level);
    }

    /**
     * Places the blueprint: the positions the schematic leaves as air that hold a solid block at or below the ground of
     * their column, such as the ground a cellar reaches into or a hill it is built into, must be dug out; the other air
     * positions ask for nothing, so plants, snow, leaves and what stands above the ground stay. The blueprint then
     * covers no more than its blocks, the positions to dig and its fluid sources.
     *
     * @param world   name of the world
     * @param x       x of the point the owner stands on
     * @param y       y of that point
     * @param z       z of that point
     * @param types   reads the world, on the main thread
     * @param surface the height of the ground of a column by its x and z, leaves and plants left out
     * @return the placed blueprint
     * @throws IllegalArgumentException when it asks for nothing there
     */
    public Blueprint dug(String world, int x, int y, int z, BuildCheck.Types types, IntBinaryOperator surface) {
        var area = area(world, x, y, z);
        List<Want> placedPalette = new ArrayList<>(palette);
        var digId = 0;
        var grid = cells.clone();
        for (var dy = 0; dy < height; dy++) {
            for (var dz = 0; dz < length; dz++) {
                for (var dx = 0; dx < width; dx++) {
                    var i = (dy * length + dz) * width + dx;
                    if (grid[i] != UNPLACED_AIR) continue;
                    int wx = area.minX() + dx, wy = area.minY() + dy, wz = area.minZ() + dz;
                    var type = types.at(wx, wy, wz);
                    if (!type.isSolid() || !wanted(type) || wy > surface.applyAsInt(wx, wz)) {
                        grid[i] = 0;
                        continue;
                    }
                    if (digId == 0) {
                        placedPalette.add(DIG);
                        digId = placedPalette.size();
                    }
                    grid[i] = digId;
                }
            }
        }
        int minX = width, minY = height, minZ = length, maxX = -1, maxY = -1, maxZ = -1;
        for (var dy = 0; dy < height; dy++) {
            for (var dz = 0; dz < length; dz++) {
                for (var dx = 0; dx < width; dx++) {
                    if (grid[(dy * length + dz) * width + dx] == 0) continue;
                    minX = Math.min(minX, dx);
                    minY = Math.min(minY, dy);
                    minZ = Math.min(minZ, dz);
                    maxX = Math.max(maxX, dx);
                    maxY = Math.max(maxY, dy);
                    maxZ = Math.max(maxZ, dz);
                }
            }
        }
        if (maxX < 0) throw new IllegalArgumentException("nothing to build or dig here");
        for (var fluid : fluids) {
            minX = Math.min(minX, fluid.x());
            minY = Math.min(minY, fluid.y());
            minZ = Math.min(minZ, fluid.z());
            maxX = Math.max(maxX, fluid.x());
            maxY = Math.max(maxY, fluid.y());
            maxZ = Math.max(maxZ, fluid.z());
        }
        int w = maxX - minX + 1, h = maxY - minY + 1, l = maxZ - minZ + 1;
        var cropped = new int[w * h * l];
        for (var dy = 0; dy < h; dy++) {
            for (var dz = 0; dz < l; dz++) {
                for (var dx = 0; dx < w; dx++) {
                    cropped[(dy * l + dz) * w + dx] = grid[((dy + minY) * length + dz + minZ) * width + dx + minX];
                }
            }
        }
        List<Fluid> kept = new ArrayList<>();
        for (var fluid : fluids) {
            kept.add(new Fluid(fluid.x() - minX, fluid.y() - minY, fluid.z() - minZ, fluid.type()));
        }
        return new Blueprint(
                w, h, l, placedPalette, cropped, new int[] {offsetX + minX, offsetY + minY, offsetZ + minZ}, kept);
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
        return id <= 0 ? null : palette.get(id - 1);
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
                && cells().equals(blueprint.cells())
                && fluids.equals(blueprint.fluids);
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, length, blocks, fluids);
    }

    /**
     * @param left the block types to leave out
     * @return this blueprint with the positions of those types asking for nothing, as when the schematic had them as
     *     air above the ground
     */
    public Blueprint without(Predicate<Material> left) {
        var kept = cells.clone();
        for (var i = 0; i < kept.length; i++) {
            if (kept[i] > 0 && left.test(palette.get(kept[i] - 1).type())) kept[i] = 0;
        }
        return new Blueprint(width, height, length, palette, kept, new int[] {offsetX, offsetY, offsetZ}, fluids);
    }

    /**
     * @return the water and lava sources of the schematic, at offsets from the lowest corner of the area; they ask for
     *     nothing, but a job whose owner supplies the materials stocks and places them
     */
    public List<Fluid> fluids() {
        return fluids;
    }

    /**
     * @param area the area the blueprint was placed on
     * @param x    x of a block
     * @param y    y of a block
     * @param z    z of a block
     * @return the fluid whose source the schematic has there, {@link Material#WATER} or {@link Material#LAVA}; null for
     *     none
     */
    public @Nullable Material fluid(JobArea area, int x, int y, int z) {
        int dx = x - area.minX(), dy = y - area.minY(), dz = z - area.minZ();
        for (var fluid : fluids) {
            if (fluid.x() == dx && fluid.y() == dy && fluid.z() == dz) return fluid.type();
        }
        return null;
    }

    /**
     * @return how many positions ask for each block, the positions to dig out left out
     */
    public Map<Want, Integer> wants() {
        Map<Want, Integer> wants = new LinkedHashMap<>();
        for (var cell : cells) {
            if (cell > 0 && !palette.get(cell - 1).type().isAir()) {
                wants.merge(palette.get(cell - 1), 1, Integer::sum);
            }
        }
        return wants;
    }

    /**
     * @return how many blocks of each type the blueprint asks for
     */
    public Map<Material, Integer> counts() {
        Map<Material, Integer> counts = new LinkedHashMap<>();
        for (var cell : cells) {
            if (cell > 0 && !palette.get(cell - 1).type().isAir()) {
                counts.merge(palette.get(cell - 1).type(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * @return every position that asks for a block, at offsets from the lowest corner of the area; a position to dig
     *     out asks for air
     */
    public List<Cell> cells() {
        List<Cell> found = new ArrayList<>();
        for (var y = 0; y < height; y++) {
            for (var z = 0; z < length; z++) {
                for (var x = 0; x < width; x++) {
                    var id = cells[(y * length + z) * width + x];
                    if (id <= 0) continue;
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
     * @return how many positions must be dug out
     */
    public int digs() {
        return digs;
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
            for (var cell : cells) out.writeShort(Math.max(0, cell));
            out.writeInt(fluids.size());
            for (var fluid : fluids) {
                out.writeInt(fluid.x());
                out.writeInt(fluid.y());
                out.writeInt(fluid.z());
                out.writeUTF(key(fluid.type()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    /**
     * @param text a blueprint as {@link #encode()} wrote it, one stored before blueprints kept their fluids, which has
     *     none, or one stored before blueprints kept block data, which asks for types alone
     * @return the blueprint; a block type this server does not know asks for nothing, and air asks to be dug out
     */
    public static Blueprint decode(String text) {
        try (var in = new DataInputStream(
                new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text))))) {
            var first = in.readInt();
            var withFluids = first == FORMAT;
            var withData = withFluids || first == FORMAT_WITHOUT_FLUIDS;
            int width = withData ? in.readInt() : first, height = in.readInt(), length = in.readInt();
            var size = in.readInt();
            List<Want> palette = new ArrayList<>();
            var known = new boolean[size];
            for (var i = 0; i < size; i++) {
                var want = want(in.readUTF());
                known[i] = want != null;
                palette.add(want == null ? DIG : want);
            }
            var cells = new int[width * height * length];
            for (var i = 0; i < cells.length; i++) {
                var id = in.readUnsignedShort();
                cells[i] = id > 0 && known[id - 1] ? id : 0;
            }
            List<Fluid> fluids = new ArrayList<>();
            var count = withFluids ? in.readInt() : 0;
            for (var i = 0; i < count; i++) {
                int x = in.readInt(), y = in.readInt(), z = in.readInt();
                var type = Material.matchMaterial(in.readUTF());
                if (type == Material.WATER || type == Material.LAVA) fluids.add(new Fluid(x, y, z, type));
            }
            return new Blueprint(width, height, length, palette, cells, new int[3], fluids);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static @Nullable Want want(String stored) {
        var open = stored.indexOf('[');
        var type = Material.matchMaterial(open < 0 ? stored : stored.substring(0, open));
        if (type == null || (type.isAir() && !stored.equals(DIG.data()))) return null;
        return new Want(type, stored.contains(":") ? stored : key(type));
    }
}
