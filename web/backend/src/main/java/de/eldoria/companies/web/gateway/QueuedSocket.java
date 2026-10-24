/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A {@link Socket} that sends from its own virtual thread, so the caller never waits for a slow peer. A peer that
 * lets more than {@value #CAPACITY} messages pile up is closed with {@value #CLOSE_TOO_SLOW}. A close waits behind
 * the messages queued before it; on a full queue those are dropped and the close goes out at once with its own code.
 * <p>
 * Browser streams use it: patches arrive on a node's connection thread, which must not stall for one slow browser.
 * Node connections keep blocking sends, which pace the gateway to the node.
 */
public final class QueuedSocket implements Socket {
    /**
     * Close code for a peer that does not keep up.
     */
    public static final int CLOSE_TOO_SLOW = 1013;

    private static final int CAPACITY = 256;
    private final Socket socket;
    private final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(CAPACITY);
    private final Thread sender;
    private volatile boolean stopped;

    /**
     * @param socket the socket to send on
     */
    public QueuedSocket(Socket socket) {
        this.socket = socket;
        this.sender = Thread.ofVirtual().name("stream-sender").start(this::drain);
    }

    @Override
    public void send(String text) {
        if (stopped) return;
        if (!queue.offer(() -> socket.send(text))) stop(CLOSE_TOO_SLOW, "Too slow");
    }

    @Override
    public void close(int code, String reason) {
        if (stopped) return;
        if (!queue.offer(() -> {
            stopped = true;
            socket.close(code, reason);
        })) {
            stop(code, reason);
        }
    }

    @Override
    public void limit(long bytes) {
        socket.limit(bytes);
    }

    @Override
    public boolean isOpen() {
        return !stopped && socket.isOpen();
    }

    private void stop(int code, String reason) {
        stopped = true;
        sender.interrupt();
        queue.clear();
        socket.close(code, reason);
    }

    private void drain() {
        try {
            while (!stopped) queue.take().run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
