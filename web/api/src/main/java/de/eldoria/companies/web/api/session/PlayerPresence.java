/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.session;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Plugin to gateway: a player with a dashboard session joined or left the node that sends it. A node sends it for every
 * such player online on it right after it was welcomed, so the gateway knows where to route the actions that move
 * items, which run with the player's inventory. A gateway that does not know the message ignores it.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param player the player
 * @param online whether the player is now online on that node
 * @param node the node the player joined or left; none means the sending node, a node relaying for others names it
 */
@JsonTypeName("player.presence")
public record PlayerPresence(
        @Nullable String id,
        int v,
        UUID player,
        boolean online,
        @Nullable UUID node) implements Message {}
