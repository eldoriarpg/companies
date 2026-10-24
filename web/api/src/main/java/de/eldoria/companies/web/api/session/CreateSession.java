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
 * Plugin to gateway: asks for a pending session for a player who ran {@code /company dashboard}; answered with {@link
 * SessionCreated}.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param player the player
 * @param node the node the player is on
 */
@JsonTypeName("session.create")
public record CreateSession(@Nullable String id, int v, PlayerRef player, UUID node) implements Message {}
