/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.update;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/**
 * Plugin to gateway: a change of the snapshots of the given sessions. Each node numbers its patches; a gap makes the
 * gateway ask for a fresh snapshot.
 * <p>
 * The lists {@code orders} and {@code company.orders} (keyed by order id) and {@code company.members} (keyed by member
 * uuid) change entry by entry with {@link PatchOp#UPSERT} and {@link PatchOp#REMOVE}. The paths {@code player},
 * {@code company}, {@code player.invites} and {@code rankings} are whole values, changed with {@link PatchOp#REPLACE}
 * ({@code company} with {@link PatchOp#REMOVE} when the player has none), as are {@code player.unreadInbox} and
 * {@code company.treasury}.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param sessions the sessions it applies to
 * @param node the node that sent it
 * @param seq the node's sequence number of this patch
 * @param path the dotted path into the snapshot, e.g. {@code company.members}
 * @param op how the value at the path changes
 * @param key the list entry's key for {@link PatchOp#UPSERT} and {@link PatchOp#REMOVE} on lists, e.g. a member's id
 * @param value the new value, none for {@link PatchOp#REMOVE}
 */
@JsonTypeName("patch")
public record Patch(
        @Nullable String id,
        int v,
        List<String> sessions,
        UUID node,
        long seq,
        String path,
        PatchOp op,
        @Nullable String key,
        @Nullable JsonNode value)
        implements Message {}
