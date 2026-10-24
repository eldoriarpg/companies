/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A {@link Socket} over a Jetty websocket session. A send waits up to ten seconds for the frame to be written, so one
 * slow peer cannot pile up frames; on failure the socket is closed.
 */
public final class JettySocket implements Socket {
    private static final Logger log = LoggerFactory.getLogger(JettySocket.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private final Session session;

    /**
     * @param session the Jetty session
     */
    public JettySocket(Session session) {
        this.session = session;
    }

    @Override
    public synchronized void send(String text) {
        if (!session.isOpen()) return;
        Callback.Completable sent = new Callback.Completable();
        session.sendText(text, sent);
        try {
            sent.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            log.debug("Sending failed, closing the socket", e);
            session.disconnect();
        }
    }

    @Override
    public void close(int code, String reason) {
        if (session.isOpen()) session.close(code, reason, Callback.NOOP);
    }

    @Override
    public void limit(long bytes) {
        session.setMaxTextMessageSize(bytes);
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }
}
