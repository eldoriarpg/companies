/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

/**
 * One end of a websocket, to a node or to a browser. Sends are serialised, so any thread may send.
 */
public interface Socket {
    /**
     * Sends a text frame and waits until it is written. A closed or failing socket drops the text.
     *
     * @param text the text
     */
    void send(String text);

    /**
     * Closes the socket.
     *
     * @param code the close code
     * @param reason the close reason
     */
    void close(int code, String reason);

    /**
     * @return whether the socket is still open
     */
    boolean isOpen();
}
