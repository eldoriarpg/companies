/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the schema of the current protocol version for {@code ./gradlew :web-api:generateSchema}.
 */
public final class ProtocolSchemaCli {
    private ProtocolSchemaCli() {}

    /**
     * Writes the schema to {@link ProtocolSchema#PATH}, or to the path given as the first argument.
     *
     * @param args an optional target path
     * @throws IOException when the file cannot be written
     */
    public static void main(String[] args) throws IOException {
        Path target = args.length > 0 ? Path.of(args[0]) : ProtocolSchema.PATH;
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.writeString(target, ProtocolSchema.generate());
        System.out.println("Wrote " + target.toAbsolutePath());
    }
}
