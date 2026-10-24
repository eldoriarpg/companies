/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.node;

import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.session.CreateSession;
import de.eldoria.companies.web.api.session.SessionClosed;
import de.eldoria.companies.web.api.session.SessionsRevoked;
import de.eldoria.companies.web.api.update.Patch;

/**
 * What the {@link NodeHub} hands on from the nodes. Calls arrive on the connection's thread, in the order the node
 * sent them; none may wait for an answer of a node.
 */
public interface NodeEvents {
    /**
     * A player asked for a dashboard session.
     *
     * @param server the player's server
     * @param link the node that asked
     * @param request the request
     * @return the answer: the created session or an error
     */
    Message createSession(LiveServer server, NodeLink link, CreateSession request);

    /**
     * The plugin ended one session.
     *
     * @param server the server
     * @param message the message
     */
    void sessionClosed(LiveServer server, SessionClosed message);

    /**
     * The plugin ended every session of a player.
     *
     * @param server the server
     * @param message the message
     */
    void sessionsRevoked(LiveServer server, SessionsRevoked message);

    /**
     * A node pushed a change of snapshots.
     *
     * @param server the server
     * @param link the node
     * @param patch the change
     */
    void patch(LiveServer server, NodeLink link, Patch patch);

    /**
     * A node was welcomed and takes requests.
     *
     * @param server the server
     * @param link the node
     */
    void nodeReady(LiveServer server, NodeLink link);

    /**
     * The version policy discontinued a connected node's version; it no longer takes requests.
     *
     * @param server the server
     * @param link the node
     */
    void nodeDiscontinued(LiveServer server, NodeLink link);

    /**
     * A node changed the server's branding, or connected on a discontinued version.
     *
     * @param server the server
     */
    void serverChanged(LiveServer server);

    /**
     * The last node of a server went away.
     *
     * @param server the server
     */
    void serverOffline(LiveServer server);
}
