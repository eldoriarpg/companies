/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import org.postgresql.PGConnection;
import org.postgresql.ds.PGSimpleDataSource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.chojo.sadu.queries.api.call.Call.call;

/**
 * The bus over {@code LISTEN} and {@code NOTIFY} of PostgreSQL, on one channel named after the channel prefix.
 * <p>
 * Messages are published in order from one thread with {@code SELECT pg_notify(channel, payload)} on a pooled
 * connection; a message that cannot be sent is dropped. A message longer than {@value #MAX_PAYLOAD_BYTES} bytes is
 * never sent, since PostgreSQL refuses payloads near 8000 bytes; this is logged once per message type.
 * <p>
 * One connection of its own, outside the pool, listens on a virtual thread: the pool would hand it back after
 * {@code maxLifetime}, and a listening session must stay open. It waits for notifications and runs {@code SELECT 1}
 * every {@link Timings#pingInterval()}, so a dead connection is noticed even when no message comes. When it cannot
 * connect or loses the connection, the bus turns {@link State#DISCONNECTED} and retries with a backoff from one second
 * up to {@link Timings#maxBackoff()} until it listens again. Notifications sent meanwhile are lost, as with Redis
 * pub/sub, so the pollers stay in charge until then.
 * <p>
 * Received messages reach every node including the sender and are handed to the handler executor (see
 * {@link MessageDispatch}). Log messages name the server, never the password. It does not work through a connection
 * pooler in transaction mode, since {@code LISTEN} needs a session.
 */
public final class PostgresNotifyBus implements NodeBus {
    /**
     * Longest message sent, in bytes of UTF-8. PostgreSQL refuses payloads of 8000 bytes and more.
     */
    static final int MAX_PAYLOAD_BYTES = 7900;

    /**
     * Longest channel name PostgreSQL keeps, in bytes; longer identifiers are cut.
     */
    static final int MAX_CHANNEL_BYTES = 63;

    private static final Duration MIN_BACKOFF = Duration.ofSeconds(1);
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int SOCKET_TIMEOUT_SECONDS = 10;
    private static final long STOP_WAIT_MILLIS = 2000;
    private static final String FALLBACK = "Notifications and caches fall back to polling until it is back.";

    private final Server server;
    private final UUID node;
    private final Logger logger;
    private final Timings timings;
    private final Notifier notifier;
    private final MessageDispatch dispatch;
    private final PGSimpleDataSource dataSource;
    private final Set<String> oversized = ConcurrentHashMap.newKeySet();
    private final ExecutorService publisher = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("companies-notify-publisher").factory());
    private final CountDownLatch firstAttempt = new CountDownLatch(1);
    private final Thread listenerThread;
    private volatile State state = State.DISCONNECTED;
    private volatile boolean running = true;

    private PostgresNotifyBus(
            Server server, UUID node, Notifier notifier, Executor handlers, Logger logger, Timings timings) {
        this.server = server;
        this.node = node;
        this.notifier = notifier;
        this.logger = logger;
        this.timings = timings;
        this.dispatch = new MessageDispatch(handlers, logger);
        this.dataSource = server.dataSource("companies-" + node);
        this.listenerThread =
                Thread.ofVirtual().name("companies-notify-listener").unstarted(this::listenLoop);
    }

    /**
     * Starts the bus and waits until the first connection attempt succeeded or failed, at most for the given time.
     * When it succeeded, the bus is {@link State#CONNECTED} on return, so whoever reads the state right after starting
     * sees it. The bus keeps trying in the background either way.
     *
     * @param server   the database to listen on and the channel
     * @param node     uid of this node, sent with every message
     * @param notifier sends a notification over a pooled connection, usually {@link Notifier#pooled()}
     * @param handlers runs the handlers of received messages
     * @param logger   reports connects, losses and refused messages
     * @param wait     longest wait for the first attempt
     * @return the started bus, listening or retrying
     */
    public static PostgresNotifyBus start(
            Server server, UUID node, Notifier notifier, Executor handlers, Logger logger, Duration wait) {
        return start(server, node, notifier, handlers, logger, wait, Timings.DEFAULT);
    }

    /**
     * Starts the bus with other timings, see {@link #start(Server, UUID, Notifier, Executor, Logger, Duration)}.
     *
     * @param server   the database to listen on and the channel
     * @param node     uid of this node, sent with every message
     * @param notifier sends a notification over a pooled connection
     * @param handlers runs the handlers of received messages
     * @param logger   reports connects, losses and refused messages
     * @param wait     longest wait for the first attempt
     * @param timings  how often to check the listening connection and the longest backoff
     * @return the started bus, listening or retrying
     */
    static PostgresNotifyBus start(
            Server server,
            UUID node,
            Notifier notifier,
            Executor handlers,
            Logger logger,
            Duration wait,
            Timings timings) {
        var bus = new PostgresNotifyBus(server, node, notifier, handlers, logger, timings);
        bus.listenerThread.start();
        try {
            bus.firstAttempt.await(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return bus;
    }

    /**
     * Whether a channel prefix fits as channel name.
     *
     * @param channel the channel prefix
     * @return true when PostgreSQL keeps the whole name
     */
    static boolean fits(String channel) {
        return channel.getBytes(StandardCharsets.UTF_8).length <= MAX_CHANNEL_BYTES;
    }

    @Override
    public void publish(NodeMessage message) {
        if (!running) return;
        var type = NodeMessageCodec.type(message.getClass());
        var text = dispatch.codec().encode(node, message);
        var size = text.getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_PAYLOAD_BYTES) {
            refuse(type, size);
            return;
        }
        try {
            publisher.execute(() -> send(text));
        } catch (RejectedExecutionException e) {
            logger.log(Level.FINE, "Dropped a PostgreSQL notification while shutting down.", e);
        }
    }

    private void refuse(String type, int size) {
        if (oversized.add(type)) {
            logger.warning("A node message of type " + type + " has " + size + " bytes, more than the "
                    + MAX_PAYLOAD_BYTES + " PostgreSQL notify carries. It is not sent; the other servers pick the"
                    + " change up by polling.");
        } else {
            logger.log(Level.FINE, "Did not send a node message of type " + type + " with " + size + " bytes.");
        }
    }

    private void send(String text) {
        try {
            notifier.notify(server.channel(), text);
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Could not send a PostgreSQL notification at " + server.address() + ".", e);
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
        return Transport.POSTGRES_NOTIFY;
    }

    /**
     * @return the database this bus listens on, without credentials
     */
    public String address() {
        return server.address();
    }

    @Override
    public void close() {
        running = false;
        listenerThread.interrupt();
        publisher.shutdown();
        try {
            if (!publisher.awaitTermination(STOP_WAIT_MILLIS, TimeUnit.MILLISECONDS)) publisher.shutdownNow();
            listenerThread.join(Duration.ofMillis(STOP_WAIT_MILLIS));
        } catch (InterruptedException e) {
            publisher.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void listenLoop() {
        var backoff = MIN_BACKOFF;
        var reported = false;
        while (running) {
            var listened = false;
            try (var connection = dataSource.getConnection()) {
                listen(connection);
                listened = true;
                connected();
                receive(connection);
            } catch (SQLException | RuntimeException e) {
                if (running) {
                    if (listened) {
                        logger.warning("Lost the PostgreSQL notify connection to " + server.address() + ": "
                                + RedisBus.reason(e) + ". " + FALLBACK);
                    } else if (!reported) {
                        logger.warning("Could not listen for PostgreSQL notifications at " + server.address() + ": "
                                + RedisBus.reason(e) + ". " + FALLBACK);
                    } else {
                        logger.log(
                                Level.FINE,
                                "Could not listen for PostgreSQL notifications at " + server.address() + ".",
                                e);
                    }
                }
            }
            if (listened) {
                backoff = MIN_BACKOFF;
                reported = false;
            } else {
                reported = true;
            }
            firstAttempt.countDown();
            if (!running) return;
            changeState(State.DISCONNECTED);
            if (!pause(backoff)) return;
            var doubled = backoff.multipliedBy(2);
            backoff = doubled.compareTo(timings.maxBackoff()) > 0 ? timings.maxBackoff() : doubled;
        }
    }

    private void listen(Connection connection) throws SQLException {
        var channel = connection.unwrap(PGConnection.class).escapeIdentifier(server.channel());
        try (var statement = connection.createStatement()) {
            statement.execute("LISTEN " + channel);
        }
    }

    private void connected() {
        logger.info("Listening for PostgreSQL notifications at " + server.address() + ".");
        changeState(State.CONNECTED);
        firstAttempt.countDown();
    }

    private void receive(Connection connection) throws SQLException {
        var postgres = connection.unwrap(PGConnection.class);
        var wait = (int) Math.max(1, timings.pingInterval().toMillis());
        var lastCheck = System.nanoTime();
        while (running) {
            var notifications = postgres.getNotifications(wait);
            if (notifications != null) {
                for (var notification : notifications) {
                    if (server.channel().equals(notification.getName())) dispatch.receive(notification.getParameter());
                }
            }
            if (System.nanoTime() - lastCheck >= timings.pingInterval().toNanos()) {
                check(connection);
                lastCheck = System.nanoTime();
            }
        }
    }

    private static void check(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("SELECT 1");
        }
    }

    private boolean pause(Duration duration) {
        try {
            Thread.sleep(duration);
            return running;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void changeState(State next) {
        if (state == next) return;
        state = next;
        dispatch.stateChanged(next);
    }

    /**
     * Sends one notification.
     */
    @FunctionalInterface
    public interface Notifier {
        /**
         * Sends a notification; blocks until it is committed.
         *
         * @param channel the channel
         * @param payload the message as JSON text
         * @throws RuntimeException when it could not be sent
         */
        void notify(String channel, String payload);

        /**
         * Runs {@code SELECT pg_notify(?, ?)} on a connection of the pool of the default query configuration, which
         * is read on the first notification.
         *
         * @return the notifier
         */
        static Notifier pooled() {
            return new PooledNotifier();
        }
    }

    /**
     * Sends notifications through SADU on the pool of the default query configuration, set to throw failures instead
     * of logging them.
     */
    private static final class PooledNotifier implements Notifier {
        private volatile QueryConfiguration queries;

        @Override
        public void notify(String channel, String payload) {
            queries()
                    .query("SELECT pg_notify(?, ?)")
                    .single(call().bind(channel).bind(payload))
                    .map(row -> true)
                    .first();
        }

        private QueryConfiguration queries() {
            var current = queries;
            if (current != null) return current;
            current = QueryConfiguration.getDefault()
                    .edit()
                    .setExceptionHandler(error -> {})
                    .setThrowExceptions(true)
                    .build();
            queries = current;
            return current;
        }
    }

    /**
     * The PostgreSQL server, how to log in and the channel.
     *
     * @param host     address of the server
     * @param port     port of the server
     * @param database name of the database
     * @param user     the database user
     * @param password the password; never logged
     * @param channel  the channel, the network's channel prefix
     */
    public record Server(String host, int port, String database, String user, String password, String channel) {
        /**
         * @return host, port and database, for messages
         */
        public String address() {
            return host + ":" + port + "/" + database;
        }

        private PGSimpleDataSource dataSource(String applicationName) {
            var source = new PGSimpleDataSource();
            source.setServerNames(new String[] {host});
            source.setPortNumbers(new int[] {port});
            source.setDatabaseName(database);
            source.setUser(user);
            source.setPassword(password);
            source.setApplicationName(applicationName);
            source.setConnectTimeout(CONNECT_TIMEOUT_SECONDS);
            source.setSocketTimeout(SOCKET_TIMEOUT_SECONDS);
            source.setTcpKeepAlive(true);
            return source;
        }

        /**
         * Describes the connection without the password.
         *
         * @return address, user and channel
         */
        @Override
        public String toString() {
            return "Server[" + address() + ", user=" + user + ", channel=" + channel + "]";
        }
    }

    /**
     * How the bus watches its listening connection.
     *
     * @param pingInterval time between two checks of the listening connection
     * @param maxBackoff   longest wait between two connection attempts
     */
    record Timings(Duration pingInterval, Duration maxBackoff) {
        /**
         * Checks every 10 seconds and retries at least every 30 seconds.
         */
        static final Timings DEFAULT = new Timings(Duration.ofSeconds(10), Duration.ofSeconds(30));
    }
}
