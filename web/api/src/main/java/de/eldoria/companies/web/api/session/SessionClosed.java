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
 * Either direction: one session ended.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param session the session id
 * @param player the player it belonged to
 * @param reason why it ended
 */
@JsonTypeName("session.closed")
public record SessionClosed(@Nullable String id, int v, String session, UUID player, CloseReason reason)
        implements Message {}
