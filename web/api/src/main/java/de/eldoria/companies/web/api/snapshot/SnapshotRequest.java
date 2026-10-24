/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Gateway to plugin: asks for the snapshot of a session; answered with {@link Snapshot}.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param session the session id
 * @param player the session's player
 */
@JsonTypeName("snapshot.request")
public record SnapshotRequest(@Nullable String id, int v, String session, UUID player) implements Message {}
