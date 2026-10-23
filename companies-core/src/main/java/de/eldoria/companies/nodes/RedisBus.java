/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The bus over Redis pub/sub, on the channels {@code <prefix>:<type>}.
 * <p>
 * One subscriber connection runs on a virtual thread and listens to {@code <prefix>:*}. When it cannot connect or
 * loses the connection, the bus turns {@link State#DISCONNECTED} and retries with a backoff from one to thirty
 * seconds until it is connected again. A watchdog pings the subscriber connection and drops it when nothing came
 * back for a while, so a silently dead connection is noticed as well. Messages are published in order from one
 * thread through a small pool of connections; a message that cannot be sent is dropped.
 * <p>
 * Received messages are handed to the handler executor, one task per handler. Messages of another format version or
 * of unknown types are skipped (see {@link NodeMessageCodec}). Log messages name the server, never the password.
 */
public final class RedisBus implements NodeBus {
    private static final Duration MIN_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);
    private static final Duration PING_INTERVAL = Duration.ofSeconds(10);
    private static final Duration SILENCE_LIMIT = Duration.ofSeconds(30);
    private static final int TIMEOUT_MILLIS = 2000;
    private static final int POOL_SIZE = 2;
    private static final String FALLBACK = "Notifications and caches fall back to polling until it is back.";

    private final Connection connection;
    private final UUID node;
    private final Executor handlers;
    private final Logger logger;
    private final NodeMessageCodec codec = new NodeMessageCodec();
    private final Map<Class<? extends NodeMessage>, List<Handler<NodeMessage>>> subscriptions =
            new ConcurrentHashMap<>();
    private final List<Consumer<State>> listeners = new CopyOnWriteArrayList<>();
    private final JedisClientConfig clientConfig;
    private final HostAndPort address;
    private final JedisPool pool;
    private final ExecutorService publisher = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("companies-redis-publisher").factory());
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("companies-redis-watchdog").daemon().factory());
    private final CountDownLatch firstAttempt = new CountDownLatch(1);
    private final Thread subscriberThread;
    private volatile State state = State.DISCONNECTED;
    private volatile boolean running = true;
    private volatile Subscriber subscriber;
    private volatile Jedis subscriberClient;
    private volatile long lastHeard;

    private RedisBus(Connection connection, UUID node, Executor handlers, Logger logger) {
        this.connection = connection;
        this.node = node;
        this.handlers = handlers;
        this.logger = logger;
        this.address = new HostAndPort(connection.host(), connection.port());
        this.clientConfig = connection.clientConfig("companies-" + node);
        var poolConfig = new GenericObjectPoolConfig<Jedis>();
        poolConfig.setMaxTotal(POOL_SIZE);
        poolConfig.setMaxIdle(POOL_SIZE);
        poolConfig.setMinIdle(0);
        poolConfig.setJmxEnabled(false);
        this.pool = new JedisPool(poolConfig, address, clientConfig);
        this.subscriberThread =
                Thread.ofVirtual().name("companies-redis-subscriber").unstarted(this::subscribeLoop);
    }

    /**
     * Starts the bus and waits until the first connection attempt succeeded or failed, at most for the given time.
     * The bus keeps trying in the background either way.
     *
     * @param connection the server to connect to
     * @param node       uid of this node, sent with every message
     * @param handlers   runs the handlers of received messages
     * @param logger     reports connects and losses
     * @param wait       longest wait for the first attempt
     * @return the started bus, connected or retrying
     */
    public static RedisBus start(Connection connection, UUID node, Executor handlers, Logger logger, Duration wait) {
        var bus = new RedisBus(connection, node, handlers, logger);
        bus.subscriberThread.start();
        bus.watchdog.scheduleWithFixedDelay(
                bus::watch, PING_INTERVAL.toMillis(), PING_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        try {
            bus.firstAttempt.await(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return bus;
    }

    @Override
    public void publish(NodeMessage message) {
        if (!running) return;
        var channel = channel(NodeMessageCodec.type(message.getClass()));
        var text = codec.encode(node, message);
        try {
            publisher.execute(() -> send(channel, text));
        } catch (RejectedExecutionException e) {
            logger.log(Level.FINE, "Dropped a message to Redis while shutting down.", e);
        }
    }

    private void send(String channel, String text) {
        try (var jedis = pool.getResource()) {
            jedis.publish(channel, text);
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Could not publish to Redis at " + connection.address() + ".", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends NodeMessage> void subscribe(Class<T> type, Handler<T> handler) {
        subscriptions
                .computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>())
                .add((Handler<NodeMessage>) handler);
    }

    @Override
    public void onStateChange(Consumer<State> listener) {
        listeners.add(listener);
    }

    @Override
    public State state() {
        return state;
    }

    /**
     * @return the server this bus connects to, without credentials
     */
    public String address() {
        return connection.address();
    }

    @Override
    public void close() {
        running = false;
        watchdog.shutdownNow();
        var current = subscriber;
        if (current != null && current.isSubscribed()) {
            try {
                current.punsubscribe();
            } catch (RuntimeException e) {
                logger.log(Level.FINE, "Could not unsubscribe from Redis.", e);
            }
        }
        disconnectSubscriber();
        subscriberThread.interrupt();
        publisher.shutdown();
        try {
            if (!publisher.awaitTermination(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) publisher.shutdownNow();
        } catch (InterruptedException e) {
            publisher.shutdownNow();
            Thread.currentThread().interrupt();
        }
        pool.close();
    }

    private String channel(String type) {
        return connection.channelPrefix() + ":" + type;
    }

    private void subscribeLoop() {
        var backoff = MIN_BACKOFF;
        var reported = false;
        while (running) {
            var current = new Subscriber();
            subscriber = current;
            try (var jedis = new Jedis(address, clientConfig)) {
                subscriberClient = jedis;
                jedis.psubscribe(current, channel("*"));
            } catch (RuntimeException e) {
                if (running) {
                    if (current.connected) {
                        logger.warning("Lost the connection to Redis at " + connection.address() + ": " + e.getMessage()
                                + ". " + FALLBACK);
                    } else if (!reported) {
                        logger.warning("Could not connect to Redis at " + connection.address() + ": " + e.getMessage()
                                + ". " + FALLBACK);
                    } else {
                        logger.log(Level.FINE, "Could not connect to Redis at " + connection.address() + ".", e);
                    }
                }
            } finally {
                subscriberClient = null;
            }
            if (current.connected) {
                backoff = MIN_BACKOFF;
                reported = false;
            } else {
                reported = true;
            }
            firstAttempt.countDown();
            if (!running) return;
            changeState(State.DISCONNECTED);
            if (!pause(backoff)) return;
            backoff = backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
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

    private void watch() {
        var current = subscriber;
        if (state != State.CONNECTED || current == null || !current.isSubscribed()) return;
        if (System.nanoTime() - lastHeard > SILENCE_LIMIT.toNanos()) {
            logger.warning("Redis at " + connection.address() + " did not answer. Reconnecting.");
            disconnectSubscriber();
            return;
        }
        try {
            current.ping();
        } catch (RuntimeException e) {
            disconnectSubscriber();
        }
    }

    private void disconnectSubscriber() {
        var client = subscriberClient;
        if (client == null) return;
        try {
            client.disconnect();
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Could not close the Redis subscriber connection.", e);
        }
    }

    private void changeState(State next) {
        if (state == next) return;
        state = next;
        for (var listener : listeners) {
            try {
                listener.accept(next);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not apply the new Redis connection state.", e);
            }
        }
    }

    private void receive(String text) {
        lastHeard = System.nanoTime();
        var envelope = codec.decode(text);
        if (envelope.isEmpty()) return;
        var message = envelope.get().message();
        for (var handler : subscriptions.getOrDefault(message.getClass(), List.of())) {
            try {
                handlers.execute(() -> handle(handler, envelope.get().node(), message));
            } catch (RejectedExecutionException e) {
                return;
            }
        }
    }

    private void handle(Handler<NodeMessage> handler, UUID sender, NodeMessage message) {
        try {
            handler.handle(sender, message);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not handle the node message " + message + ".", e);
        }
    }

    /**
     * The Redis server and how to log in.
     *
     * @param host          address of the server
     * @param port          port of the server
     * @param user          user for access control lists, empty for the default user
     * @param password      the resolved password, empty for none; never logged
     * @param ssl           true to connect with TLS
     * @param database      number of the Redis database
     * @param channelPrefix prefix of every channel
     */
    public record Connection(
            String host, int port, String user, String password, boolean ssl, int database, String channelPrefix) {
        /**
         * @return host and port, for messages
         */
        public String address() {
            return host + ":" + port;
        }

        private JedisClientConfig clientConfig(String clientName) {
            return DefaultJedisClientConfig.builder()
                    .user(user.isEmpty() ? null : user)
                    .password(password.isEmpty() ? null : password)
                    .ssl(ssl)
                    .database(database)
                    .clientName(clientName)
                    .timeoutMillis(TIMEOUT_MILLIS)
                    .build();
        }

        /**
         * Describes the connection without the password.
         *
         * @return host, port, user, database and prefix
         */
        @Override
        public String toString() {
            return "Connection[" + address() + ", user=" + user + ", ssl=" + ssl + ", database=" + database
                    + ", channelPrefix=" + channelPrefix + "]";
        }
    }

    private final class Subscriber extends JedisPubSub {
        private volatile boolean connected;

        @Override
        public void onPSubscribe(String pattern, int subscribedChannels) {
            lastHeard = System.nanoTime();
            connected = true;
            logger.info("Connected to Redis at " + connection.address() + ".");
            firstAttempt.countDown();
            changeState(State.CONNECTED);
        }

        @Override
        public void onPMessage(String pattern, String channel, String message) {
            receive(message);
        }

        @Override
        public void onPong(String pattern) {
            lastHeard = System.nanoTime();
        }
    }
}
