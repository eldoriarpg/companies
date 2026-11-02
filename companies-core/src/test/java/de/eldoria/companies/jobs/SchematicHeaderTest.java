/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The declared size and format of schematic files, read without their blocks.
 */
class SchematicHeaderTest {
    @TempDir
    Path directory;

    @Test
    void readsTheBoxOfEveryFormat() throws IOException {
        assertEquals(Optional.of(new SchematicHeader(24, false)), read(sponge(2, 3, 4)));
        assertEquals(Optional.of(new SchematicHeader(24, false)), read(spongeV3(2, 3, 4)));
        assertEquals(Optional.of(new SchematicHeader(24, true)), read(mcEdit(2, 3, 4)));
        assertEquals(".schematic", read(mcEdit(1, 1, 1)).orElseThrow().suffix());
    }

    @Test
    void readsDimensionsAsUnsigned() throws IOException {
        assertEquals(
                65535L * 65535 * 65535, read(sponge(-1, -1, -1)).orElseThrow().volume());
    }

    @Test
    void refusesWhatIsNoSchematic() throws IOException {
        assertEquals(Optional.empty(), read("nothing".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Optional.empty(), read(gzip(out -> {
            out.writeByte(10);
            out.writeUTF("Schematic");
            out.writeByte(0);
        })));
    }

    @Test
    void refusesADimensionDeclaredTwice() throws IOException {
        var twice = gzip(out -> {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(10);
            out.writeUTF("Schematic");
            dimensions(out, -1, -1, -1);
            out.writeByte(0);
            dimensions(out, 1, 1, 1);
            out.writeByte(0);
        });

        assertEquals(Optional.empty(), read(twice));
    }

    @Test
    void refusesListsNestedTooDeep() throws IOException {
        var nested = gzip(out -> {
            out.writeByte(10);
            out.writeUTF("Schematic");
            dimensions(out, 1, 1, 1);
            out.writeByte(9);
            out.writeUTF("Deep");
            for (var i = 0; i < 50_000; i++) {
                out.writeByte(9);
                out.writeInt(1);
            }
            out.writeByte(0);
            out.writeInt(0);
            out.writeByte(0);
        });

        assertEquals(Optional.empty(), read(nested));
    }

    @Test
    void stopsReadingAfterTheDecompressedLimit() throws IOException {
        var bomb = gzip(out -> {
            out.writeByte(10);
            out.writeUTF("Schematic");
            out.writeByte(7);
            out.writeUTF("BlockData");
            var length = (int) SchematicHeader.MAX_DECOMPRESSED + 1;
            out.writeInt(length);
            out.write(new byte[length]);
            dimensions(out, 1, 1, 1);
            out.writeByte(0);
        });

        assertEquals(Optional.empty(), read(bomb));
    }

    private Optional<SchematicHeader> read(byte[] file) throws IOException {
        var path = Files.createTempFile(directory, "schematic", ".gz");
        Files.write(path, file);
        return SchematicHeader.read(path);
    }

    /**
     * @return a Sponge schematic of version 2 with the given box, some metadata and block data
     */
    static byte[] sponge(int width, int height, int length) {
        return gzip(out -> {
            out.writeByte(10);
            out.writeUTF("Schematic");
            out.writeByte(3);
            out.writeUTF("Version");
            out.writeInt(2);
            out.writeByte(10);
            out.writeUTF("Metadata");
            out.writeByte(2);
            out.writeUTF("Width");
            out.writeShort(7);
            out.writeByte(0);
            out.writeByte(7);
            out.writeUTF("BlockData");
            out.writeInt(3);
            out.write(new byte[3]);
            dimensions(out, width, height, length);
            out.writeByte(0);
        });
    }

    /**
     * @return an MCEdit schematic with the given box
     */
    static byte[] mcEdit(int width, int height, int length) {
        return gzip(out -> {
            out.writeByte(10);
            out.writeUTF("Schematic");
            dimensions(out, width, height, length);
            out.writeByte(8);
            out.writeUTF("Materials");
            out.writeUTF("Alpha");
            out.writeByte(9);
            out.writeUTF("Entities");
            out.writeByte(10);
            out.writeInt(1);
            out.writeByte(0);
            out.writeByte(0);
        });
    }

    private static byte[] spongeV3(int width, int height, int length) {
        return gzip(out -> {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(10);
            out.writeUTF("Schematic");
            out.writeByte(3);
            out.writeUTF("Version");
            out.writeInt(3);
            dimensions(out, width, height, length);
            out.writeByte(0);
            out.writeByte(0);
        });
    }

    private static void dimensions(DataOutputStream out, int width, int height, int length) throws IOException {
        out.writeByte(2);
        out.writeUTF("Width");
        out.writeShort(width);
        out.writeByte(2);
        out.writeUTF("Height");
        out.writeShort(height);
        out.writeByte(2);
        out.writeUTF("Length");
        out.writeShort(length);
    }

    private static byte[] gzip(Writer writer) {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            writer.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * Writes the NBT of a test file.
     */
    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }
}
