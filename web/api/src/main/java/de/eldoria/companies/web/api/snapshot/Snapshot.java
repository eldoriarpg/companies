/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Plugin to gateway: everything a session shows at first, as the player may see it. Large lists are separate, paged
 * requests. Patches with a higher sequence of the same node follow.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param session the session id
 * @param node the node that built it
 * @param seq the node's patch sequence at the time it was built
 * @param player the player
 * @param company the player's company, none without one
 * @param orders the orders the player placed
 * @param rankings the first page of the default ranking with the company's own place
 */
@JsonTypeName("snapshot")
public record Snapshot(
        @Nullable String id,
        int v,
        String session,
        UUID node,
        long seq,
        PlayerView player,
        @Nullable CompanyView company,
        List<OwnOrderView> orders,
        RankingPage rankings)
        implements Message {}
