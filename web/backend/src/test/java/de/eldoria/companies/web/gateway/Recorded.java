/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.api.Protocol;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The protocol messages recorded in {@code web/api/src/test/resources/messages}, one directory per protocol version.
 */
final class Recorded {
    private static final JsonMapper MAPPER = Protocol.mapper();

    private Recorded() {}

    static Path directory() {
        return Path.of(System.getProperty("companies.messages", "../api/src/test/resources/messages"));
    }

    static List<Path> files() {
        try (Stream<Path> versions = Files.list(directory())) {
            return versions.sorted()
                    .flatMap(version -> {
                        try (Stream<Path> files = Files.list(version)) {
                            return files.sorted().toList().stream();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ObjectNode message(String name) {
        return (ObjectNode) MAPPER.readTree(
                directory().resolve("v" + Protocol.VERSION).resolve(name).toFile());
    }

    /**
     * The recorded snapshot as the answer to a request.
     */
    static String snapshot(String id, String session, UUID node, long seq) {
        ObjectNode snapshot = message("snapshot.json");
        snapshot.put("id", id)
                .put("session", session)
                .put("node", node.toString())
                .put("seq", seq);
        return snapshot.toString();
    }
}
