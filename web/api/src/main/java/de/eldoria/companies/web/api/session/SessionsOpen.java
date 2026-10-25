/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.session;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Plugin to gateway, after every welcome: the ids of every session the plugin still knows, waiting ones included. The
 * gateway ends the sessions of the server it holds and the plugin no longer knows, and answers a {@link SessionClosed}
 * for each listed session it no longer holds, so sessions ended on one side while the node was disconnected end on
 * the other side too.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param sessions the sessions
 */
@JsonTypeName("session.list")
public record SessionsOpen(@Nullable String id, int v, List<Entry> sessions) implements Message {
    /**
     * A session the plugin knows.
     *
     * @param session the session id
     * @param player the player it belongs to
     */
    public record Entry(String session, UUID player) {}
}
