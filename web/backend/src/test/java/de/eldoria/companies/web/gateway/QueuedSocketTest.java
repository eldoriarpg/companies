/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueuedSocketTest {
    @Test
    void sendsInOrderAndClosesAfterTheQueuedMessages() throws InterruptedException {
        Recording peer = new Recording(new CountDownLatch(0));
        QueuedSocket socket = new QueuedSocket(peer);
        socket.send("a");
        socket.send("b");
        socket.close(4401, "Session ended");
        assertTrue(peer.closedLatch.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("a", "b", "close 4401"), peer.events);
    }

    @Test
    void closesAPeerThatDoesNotKeepUpWithoutBlockingTheCaller() throws InterruptedException {
        CountDownLatch stuck = new CountDownLatch(1);
        Recording peer = new Recording(stuck);
        QueuedSocket socket = new QueuedSocket(peer);
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) socket.send("patch " + i);
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 2);
        assertTrue(peer.closedLatch.await(5, TimeUnit.SECONDS));
        assertTrue(peer.events.contains("close " + QueuedSocket.CLOSE_TOO_SLOW));
        assertFalse(socket.isOpen());
        stuck.countDown();
    }

    @Test
    void aCloseOnAFullQueueKeepsItsCode() throws InterruptedException {
        CountDownLatch stuck = new CountDownLatch(1);
        Recording peer = new Recording(stuck);
        QueuedSocket socket = new QueuedSocket(peer);
        socket.send("first");
        assertTrue(peer.sending.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < 256; i++) socket.send("patch " + i);
        socket.close(4401, "Session ended");
        assertTrue(peer.closedLatch.await(5, TimeUnit.SECONDS));
        assertTrue(peer.events.contains("close 4401"));
        assertFalse(peer.events.contains("close " + QueuedSocket.CLOSE_TOO_SLOW));
        stuck.countDown();
    }

    private static final class Recording implements Socket {
        private final List<String> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch closedLatch = new CountDownLatch(1);
        private final CountDownLatch sending = new CountDownLatch(1);
        private final CountDownLatch release;

        private Recording(CountDownLatch release) {
            this.release = release;
        }

        @Override
        public void send(String text) {
            sending.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            events.add(text);
        }

        @Override
        public void close(int code, String reason) {
            events.add("close " + code);
            closedLatch.countDown();
        }

        @Override
        public void limit(long bytes) {}

        @Override
        public boolean isOpen() {
            return closedLatch.getCount() > 0;
        }
    }
}
