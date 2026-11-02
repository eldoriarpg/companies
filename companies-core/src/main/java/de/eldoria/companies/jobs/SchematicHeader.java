/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * The size and format a schematic file declares, read from its NBT without loading its blocks. A schematic is checked
 * against this before anything loads it, since a small gzip may declare a box of billions of blocks.
 *
 * <p>Sponge schematics of version 1 and 2 and MCEdit schematics keep {@code Width}, {@code Height} and {@code Length}
 * in their root compound, Sponge version 3 in the compound {@code Schematic} inside it. MCEdit schematics are told apart
 * by their {@code Materials} tag; WorldEdit only reads them from files ending in {@code .schematic}. A file that
 * declares a dimension twice is refused, since WorldEdit might read the other one.
 *
 * @param volume the number of blocks the declared box holds
 * @param legacy whether it is an MCEdit schematic
 */
public record SchematicHeader(long volume, boolean legacy) {
    /**
     * The most decompressed bytes read before the file counts as unreadable.
     */
    static final long MAX_DECOMPRESSED = 64L * 1024 * 1024;

    private static final int MAX_DEPTH = 64;
    private static final byte END = 0;
    private static final byte SHORT = 2;
    private static final byte COMPOUND = 10;

    /**
     * @return the suffix WorldEdit recognises this schematic's format by
     */
    public String suffix() {
        return legacy ? ".schematic" : ".schem";
    }

    /**
     * @param file a gzip compressed schematic
     * @return its header, empty when it is no schematic or declares no size
     */
    public static Optional<SchematicHeader> read(Path file) {
        try (var in = new DataInputStream(new Capped(new GZIPInputStream(Files.newInputStream(file))))) {
            if (in.readByte() != COMPOUND) return Optional.empty();
            in.readUTF();
            var found = new Found();
            compound(in, found, 0, true);
            return found.header();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static void compound(DataInputStream in, Found found, int depth, boolean root) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Nested too deep");
        for (byte type = in.readByte(); type != END; type = in.readByte()) {
            var name = in.readUTF();
            if (root && type == SHORT) {
                if (!found.dimension(name, in.readShort())) throw new IOException("Declared twice: " + name);
            } else if (root && type == COMPOUND && name.equals("Schematic")) {
                compound(in, found, depth + 1, true);
            } else {
                if (root && name.equals("Materials")) found.legacy = true;
                skip(in, type, found, depth + 1);
            }
        }
    }

    private static void skip(DataInputStream in, byte type, Found found, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Nested too deep");
        switch (type) {
            case 1 -> in.skipNBytes(1);
            case 2 -> in.skipNBytes(2);
            case 3, 5 -> in.skipNBytes(4);
            case 4, 6 -> in.skipNBytes(8);
            case 7 -> in.skipNBytes(length(in));
            case 8 -> in.skipNBytes(in.readUnsignedShort());
            case 9 -> {
                var element = in.readByte();
                var count = length(in);
                for (var i = 0; i < count; i++) skip(in, element, found, depth + 1);
            }
            case COMPOUND -> compound(in, found, depth, false);
            case 11 -> in.skipNBytes(4L * length(in));
            case 12 -> in.skipNBytes(8L * length(in));
            default -> throw new IOException("Unknown tag " + type);
        }
    }

    private static int length(DataInputStream in) throws IOException {
        var length = in.readInt();
        if (length < 0) throw new IOException("Negative length");
        return length;
    }

    /**
     * What was found while walking the root compounds.
     */
    private static final class Found {
        private int width = -1;
        private int height = -1;
        private int length = -1;
        private boolean legacy;

        /**
         * @return false when this dimension was declared before
         */
        private boolean dimension(String name, short value) {
            var size = Short.toUnsignedInt(value);
            switch (name) {
                case "Width" -> {
                    if (width >= 0) return false;
                    width = size;
                }
                case "Height" -> {
                    if (height >= 0) return false;
                    height = size;
                }
                case "Length" -> {
                    if (length >= 0) return false;
                    length = size;
                }
                default -> {}
            }
            return true;
        }

        private Optional<SchematicHeader> header() {
            if (width < 0 || height < 0 || length < 0) return Optional.empty();
            return Optional.of(new SchematicHeader((long) width * height * length, legacy));
        }
    }

    /**
     * Fails once more than {@link #MAX_DECOMPRESSED} bytes were read or skipped.
     */
    private static final class Capped extends FilterInputStream {
        private long read;

        private Capped(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            var value = super.read();
            if (value >= 0) count(1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            var count = super.read(buffer, offset, length);
            if (count > 0) count(count);
            return count;
        }

        @Override
        public long skip(long n) throws IOException {
            var skipped = super.skip(n);
            count(skipped);
            return skipped;
        }

        private void count(long bytes) throws IOException {
            read += bytes;
            if (read > MAX_DECOMPRESSED) throw new EOFException("More than " + MAX_DECOMPRESSED + " bytes");
        }
    }
}
