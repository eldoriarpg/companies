/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.session;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Gateway to plugin: the pending session the player logs in to with the short link and the code.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param session the session id
 * @param shortId the short id of the link, six characters of an unambiguous alphabet
 * @param code the six-letter login code
 * @param link the full link to open
 * @param expiresAt when the code stops working
 */
@JsonTypeName("session.created")
public record SessionCreated(
        @Nullable String id, int v, String session, String shortId, String code, String link, Instant expiresAt)
        implements Message {}
