/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.session;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Plugin to gateway: every session of a player ends, after {@code /company dashboard logout} or an admin's revoke.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param player the player
 * @param reason why the sessions end
 */
@JsonTypeName("session.revoked")
public record SessionsRevoked(@Nullable String id, int v, UUID player, CloseReason reason) implements Message {}
