/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.nodes;

import de.eldoria.companies.configuration.elements.NodeType;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * A server registered in the database.
 *
 * @param id       id of the row
 * @param uid      the {@code nodeUid} of the server's node.yml
 * @param type     whether the server runs as primary or secondary
 * @param version  plugin version the server last started with
 * @param lastSeen when the server last started or synced its configuration, null before this was recorded
 */
public record Node(
        int id,
        UUID uid,
        NodeType type,
        String version,
        @Nullable LocalDateTime lastSeen) {
    /**
     * @return when the server last started or synced its configuration, empty before this was recorded
     */
    public Optional<LocalDateTime> seen() {
        return Optional.ofNullable(lastSeen);
    }
}
