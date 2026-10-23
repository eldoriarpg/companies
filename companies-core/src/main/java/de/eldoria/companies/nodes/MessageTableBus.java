/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.data.repository.ANodeMessageData;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The bus of low latency mode: messages travel through the message table of the database.
 * <p>
 * Publishing inserts one row with the same JSON text the {@link RedisBus} sends, from one thread in the order of
 * publishing. While reading, every node reads the rows above the last id it has seen every
 * {@link Timings#readInterval()}, a page of {@value #PAGE} rows per query, and hands them to the handlers; the sender
 * receives its own messages as well, as with Redis. A node starts reading at the highest id present, so older messages
 * are not replayed. The primary deletes rows older than {@link Timings#retention()}; they are signals only.
 * <p>
 * The bus is {@link State#CONNECTED} while the table can be read. A failed read turns it {@link State#DISCONNECTED},
 * so the nodes fall back to polling, and the next successful read connects it again.
 */
public final class MessageTableBus implements NodeBus {
    /**
     * Most rows read with one query.
     */
    static final int PAGE = 500;

    private static final Duration GAP_WAIT = Duration.ofSeconds(10);
    private static final long STOP_WAIT_MILLIS = 2000;

    private final ANodeMessageData data;
    private final UUID node;
    private final Logger logger;
    private final Timings timings;
    private final MessageDispatch dispatch;
    private final ScheduledExecutorService reader = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("companies-message-reader").daemon().factory());
    private final ExecutorService publisher = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("companies-message-publisher").factory());
    private volatile State state = State.DISCONNECTED;
    private volatile boolean running = true;
    private ScheduledFuture<?> reading;
    private MessageCursor cursor;
    private boolean reported;

    private MessageTableBus(ANodeMessageData data, UUID node, Executor handlers, Logger logger, Timings timings) {
        this.data = data;
        this.node = node;
        this.logger = logger;
        this.timings = timings;
        this.dispatch = new MessageDispatch(handlers, logger);
    }

    /**
     * Creates the bus without reading yet; {@link #resume()} starts reading. The primary starts deleting old rows at
     * once.
     *
     * @param data     the message table
     * @param node     uid of this node, sent with every message
     * @param primary  true on the primary node, which deletes old rows
     * @param handlers runs the handlers of received messages
     * @param logger   reports failures
     * @param timings  how often to read and delete, and how long rows are kept
     * @return the bus, not reading
     */
    public static MessageTableBus create(
            ANodeMessageData data, UUID node, boolean primary, Executor handlers, Logger logger, Timings timings) {
        var bus = new MessageTableBus(data, node, handlers, logger, timings);
        if (primary) {
            var purge = timings.purgeInterval().toMillis();
            bus.reader.scheduleWithFixedDelay(bus::purge, purge, purge, TimeUnit.MILLISECONDS);
        }
        return bus;
    }

    /**
     * Creates the bus and starts reading.
     *
     * @param data     the message table
     * @param node     uid of this node, sent with every message
     * @param primary  true on the primary node, which deletes old rows
     * @param handlers runs the handlers of received messages
     * @param logger   reports failures
     * @param timings  how often to read and delete, and how long rows are kept
     * @return the bus, reading
     */
    public static MessageTableBus start(
            ANodeMessageData data, UUID node, boolean primary, Executor handlers, Logger logger, Timings timings) {
        var bus = create(data, node, primary, handlers, logger, timings);
        bus.resume();
        return bus;
    }

    /**
     * Starts reading from the highest id present now. Does nothing while reading already.
     */
    public synchronized void resume() {
        if (!running || reading != null) return;
        var interval = timings.readInterval().toMillis();
        reading = reader.scheduleWithFixedDelay(this::read, 0, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops reading until {@link #resume()} is called. Publishing still works.
     */
    public synchronized void pause() {
        if (reading == null) return;
        reading.cancel(false);
        reading = null;
        execute(() -> cursor = null);
    }

    @Override
    public void publish(NodeMessage message) {
        if (!running) return;
        var type = NodeMessageCodec.type(message.getClass());
        var text = dispatch.codec().encode(node, message);
        try {
            publisher.execute(() -> insert(type, text));
        } catch (RejectedExecutionException e) {
            logger.log(Level.FINE, "Dropped a node message while shutting down.", e);
        }
    }

    private void insert(String type, String text) {
        try {
            data.insert(node, type, text, LocalDateTime.now());
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Could not write a node message to the database.", e);
        }
    }

    @Override
    public <T extends NodeMessage> void subscribe(Class<T> type, Handler<T> handler) {
        dispatch.subscribe(type, handler);
    }

    @Override
    public void onStateChange(Consumer<State> listener) {
        dispatch.onStateChange(listener);
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public Transport transport() {
        return Transport.LOW_LATENCY;
    }

    @Override
    public void close() {
        running = false;
        reader.shutdownNow();
        publisher.shutdown();
        try {
            if (!publisher.awaitTermination(STOP_WAIT_MILLIS, TimeUnit.MILLISECONDS)) publisher.shutdownNow();
        } catch (InterruptedException e) {
            publisher.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void read() {
        try {
            if (cursor == null) cursor = new MessageCursor(data.maxId(), GAP_WAIT);
            var now = System.nanoTime();
            cursor.expire(now);
            var from = cursor.readFrom();
            List<ANodeMessageData.StoredMessage> page;
            do {
                page = data.after(from, PAGE);
                for (var message : page) {
                    if (cursor.accept(message.id(), now)) dispatch.receive(message.payload());
                }
                if (!page.isEmpty()) from = page.getLast().id();
            } while (page.size() == PAGE && running);
            connected();
        } catch (RuntimeException e) {
            failed(e);
        }
    }

    private void connected() {
        if (reported) logger.info("Reading node messages from the database again.");
        reported = false;
        changeState(State.CONNECTED);
    }

    private void failed(RuntimeException error) {
        if (!running) return;
        if (!reported) {
            logger.warning("Could not read node messages from the database: " + RedisBus.reason(error)
                    + ". Notifications and caches fall back to polling until it works again.");
            reported = true;
        } else {
            logger.log(Level.FINE, "Could not read node messages from the database.", error);
        }
        changeState(State.DISCONNECTED);
    }

    private void purge() {
        try {
            data.purgeBefore(LocalDateTime.now().minus(timings.retention()));
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Could not delete old node messages.", e);
        }
    }

    private void changeState(State next) {
        if (state == next) return;
        state = next;
        dispatch.stateChanged(next);
    }

    private void execute(Runnable task) {
        try {
            reader.execute(task);
        } catch (RejectedExecutionException e) {
            logger.log(Level.FINE, "The node message reader is stopped.", e);
        }
    }

    /**
     * How the bus reads and cleans up the table.
     *
     * @param readInterval  time between two reads
     * @param retention     how long rows are kept
     * @param purgeInterval time between two deletions of old rows on the primary
     */
    public record Timings(Duration readInterval, Duration retention, Duration purgeInterval) {
        /**
         * Reads every second, keeps rows for a minute and deletes old ones every 30 seconds.
         */
        public static final Timings DEFAULT =
                new Timings(Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofSeconds(30));
    }
}
