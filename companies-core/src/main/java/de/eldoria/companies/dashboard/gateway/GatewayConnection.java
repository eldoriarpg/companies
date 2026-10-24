/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.gateway;

import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.handshake.Hello;
import de.eldoria.companies.web.api.link.Ping;
import org.jetbrains.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The node's websocket to the gateway, held by one virtual thread.
 * <p>
 * The thread asks for the {@link Hello} first; without one, because no key is stored, it waits a minute and asks
 * again. It then connects with the {@code User-Agent} {@code Companies/<version>}, sends the hello and stays connected
 * until the gateway closes the connection or stays silent for 75 seconds. Every 30 seconds it sends a ping, which keeps
 * proxies such as Cloudflare from closing an idle connection. After a connection ends it waits as the {@link Backoff}
 * says, or six hours while the gateway calls the plugin version discontinued, and connects again. {@link #wake()} cuts
 * a wait short.
 * <p>
 * Messages arrive on the {@code inbound} consumer one after another, except answers to {@link #request requests},
 * which complete their future. Nothing here logs a key.
 */
public final class GatewayConnection implements GatewayLink {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(2);
    private static final Object WAKE = new Object();

    /**
     * The gateway closed the connection because a message was invalid.
     */
    public static final int CLOSE_INVALID = 4400;

    /**
     * The gateway refused the key.
     */
    public static final int CLOSE_UNAUTHORIZED = 4401;

    /**
     * The gateway heard nothing for too long.
     */
    public static final int CLOSE_IDLE = 4408;

    /**
     * Another connection with the same node uid replaced this one.
     */
    public static final int CLOSE_REPLACED = 4409;

    /**
     * The node sent too many messages.
     */
    public static final int CLOSE_RATE_LIMITED = 4429;

    /**
     * The code of a connection that ended without a close frame, e.g. because it could not be opened.
     */
    public static final int NO_CLOSE = -1;

    private final Endpoint endpoint;
    private final Timing timing;
    private final Supplier<Optional<Hello>> hello;
    private final Consumer<Message> inbound;
    private final BooleanSupplier discontinued;
    private final Consumer<Closed> closed;
    private final InstantSource clock;
    private final Logger logger;
    private final JsonMapper mapper = Protocol.mapper();
    private final HttpClient client;
    private final ExecutorService dispatch = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("Companies dashboard inbound").factory());
    private final Map<String, CompletableFuture<Message>> pending = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<Object> wakeups = new LinkedBlockingQueue<>();
    private volatile boolean running;
    private volatile State state = State.STOPPED;
    private volatile @Nullable Socket current;
    private @Nullable Thread thread;

    /**
     * @param endpoint     where the gateway is and how the node names itself
     * @param timing       pings, silence and waits
     * @param hello        builds the hello of a new connection, empty when no key is stored
     * @param inbound      receives the gateway's messages one after another
     * @param discontinued whether the gateway called the plugin version discontinued on the last connection
     * @param closed       receives how every connection ended
     * @param clock        the current time
     * @param logger       logger of the plugin
     */
    public GatewayConnection(
            Endpoint endpoint,
            Timing timing,
            Supplier<Optional<Hello>> hello,
            Consumer<Message> inbound,
            BooleanSupplier discontinued,
            Consumer<Closed> closed,
            InstantSource clock,
            Logger logger) {
        this.endpoint = endpoint;
        this.timing = timing;
        this.hello = hello;
        this.inbound = inbound;
        this.discontinued = discontinued;
        this.closed = closed;
        this.clock = clock;
        this.logger = logger;
        client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /**
     * Starts the connection's thread.
     */
    public synchronized void start() {
        if (thread != null) return;
        running = true;
        thread = Thread.ofVirtual().name("Companies dashboard").start(this::run);
    }

    /**
     * Closes the connection and ends its thread, waiting up to three seconds, then closes the HTTP client. A
     * connection still opening is closed without sending the hello.
     */
    public void stop() {
        Thread running;
        synchronized (this) {
            this.running = false;
            running = thread;
        }
        wake();
        var socket = current;
        if (socket != null) socket.close();
        if (running != null) {
            running.interrupt();
            try {
                running.join(Duration.ofSeconds(3));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        dispatch.shutdown();
        failPending();
        client.shutdownNow();
        state = State.STOPPED;
    }

    /**
     * Ends a wait before the next attempt, e.g. after a new key was entered.
     */
    public void wake() {
        wakeups.offer(WAKE);
    }

    /**
     * Closes the current connection, so the thread connects again after its wait.
     */
    public void reconnect() {
        var socket = current;
        if (socket != null) socket.close();
        wake();
    }

    /**
     * @return what the connection does now
     */
    public State state() {
        return state;
    }

    @Override
    public boolean send(Message message) {
        var socket = current;
        if (socket == null) return false;
        try {
            return socket.send(mapper.writeValueAsString(message));
        } catch (JacksonException e) {
            logger.log(Level.WARNING, "Could not write a dashboard message of type " + message.getClass(), e);
            return false;
        }
    }

    @Override
    public CompletableFuture<Message> request(Message request, Duration timeout) {
        var id = request.id();
        if (id == null) return CompletableFuture.failedFuture(new IllegalArgumentException("A request needs an id."));
        var answer = new CompletableFuture<Message>();
        pending.put(id, answer);
        answer.whenComplete((message, error) -> pending.remove(id));
        if (!send(request)) {
            answer.completeExceptionally(new IllegalStateException("The dashboard is not connected."));
            return answer;
        }
        return answer.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void run() {
        var backoff = timing.backoff();
        while (running) {
            Optional<Hello> greeting;
            try {
                greeting = hello.get();
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not prepare the dashboard connection.", e);
                greeting = Optional.empty();
            }
            if (greeting.isEmpty()) {
                state = State.NO_KEY;
                pause(timing.noKeyWait());
                continue;
            }
            var ended = connect(greeting.get());
            closed.accept(ended.closed());
            if (!running) break;
            if (ended.answered()) backoff.reset();
            state = State.RETRYING;
            pause(delay(ended.closed(), backoff));
        }
        state = State.STOPPED;
    }

    /**
     * The wait before the next attempt: six hours while the version is discontinued, an hour after the key was
     * refused, five minutes after another connection with the same node uid replaced this one, the longest backoff
     * when the gateway limited the rate, and the backoff otherwise.
     *
     * @param closed  how the connection ended
     * @param backoff the backoff
     * @return the wait
     */
    private Duration delay(Closed closed, Backoff backoff) {
        if (discontinued.getAsBoolean()) return timing.discontinuedRetry();
        return switch (closed.code()) {
            case CLOSE_UNAUTHORIZED -> timing.unauthorizedRetry();
            case CLOSE_REPLACED -> timing.replacedRetry();
            case CLOSE_RATE_LIMITED -> Backoff.MAX;
            default -> backoff.next();
        };
    }

    /**
     * Holds one connection until it ends.
     *
     * @param greeting the hello to send first
     * @return how it ended, and whether the gateway answered at all, which counts as a working connection
     */
    private Ended connect(Hello greeting) {
        state = State.CONNECTING;
        var socket = new Socket();
        try {
            var webSocket = client.newWebSocketBuilder()
                    .header("User-Agent", endpoint.userAgent())
                    .connectTimeout(CONNECT_TIMEOUT)
                    .buildAsync(endpoint.uri(), socket)
                    .get(CONNECT_TIMEOUT.toSeconds() * 2, TimeUnit.SECONDS);
            socket.open(webSocket);
            if (!running) return new Ended(false, new Closed(NO_CLOSE, ""));
            current = socket;
            state = State.CONNECTED;
            send(greeting);
            hold(socket);
        } catch (ExecutionException | TimeoutException e) {
            var cause = e.getCause() == null ? e : e.getCause();
            logger.log(
                    Level.FINE,
                    "Could not connect to the dashboard gateway: "
                            + cause.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        } finally {
            current = null;
            socket.close();
            failPending();
        }
        return new Ended(socket.answered, new Closed(socket.closeCode, socket.closeReason));
    }

    private void hold(Socket socket) throws InterruptedException {
        while (running && !socket.ended.isDone()) {
            try {
                socket.ended.get(timing.pingInterval().toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                if (Duration.between(socket.lastHeard, clock.instant()).compareTo(timing.silenceLimit()) > 0) {
                    logger.fine("The dashboard gateway stayed silent; connecting again.");
                    return;
                }
                send(new Ping(null, Protocol.VERSION, clock.instant()));
            } catch (ExecutionException e) {
                return;
            }
        }
    }

    private void pause(Duration wait) {
        try {
            wakeups.poll(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private void failPending() {
        for (var answer : pending.values()) {
            answer.completeExceptionally(new IllegalStateException("The dashboard connection closed."));
        }
    }

    private void received(Socket socket, String text) {
        socket.lastHeard = clock.instant();
        socket.answered = true;
        Message message;
        try {
            message = mapper.readValue(text, Message.class);
        } catch (JacksonException e) {
            logger.fine("The dashboard gateway sent a message that could not be read.");
            return;
        }
        var id = message.id();
        var answer = id == null ? null : pending.get(id);
        if (answer != null) {
            answer.complete(message);
            return;
        }
        try {
            dispatch.execute(() -> handle(message));
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Dropped a dashboard message while stopping.", e);
        }
    }

    private void handle(Message message) {
        try {
            inbound.accept(message);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not handle a dashboard message of type " + message.getClass(), e);
        }
    }

    /**
     * What the connection does.
     */
    public enum State {
        /**
         * Not started or stopped.
         */
        STOPPED,
        /**
         * Waiting for a key to be entered.
         */
        NO_KEY,
        /**
         * Opening the websocket.
         */
        CONNECTING,
        /**
         * Connected to the gateway.
         */
        CONNECTED,
        /**
         * Waiting before the next attempt.
         */
        RETRYING
    }

    /**
     * Where the gateway is and how the node names itself.
     *
     * @param uri       the gateway's websocket address
     * @param userAgent {@code Companies/<version>}
     */
    public record Endpoint(URI uri, String userAgent) {}

    /**
     * The times of the connection.
     *
     * @param pingInterval      how often a ping is sent
     * @param silenceLimit      how long the gateway may stay silent before the node connects again
     * @param noKeyWait         how long to wait for a key before looking again
     * @param discontinuedRetry how long to wait while the plugin version is discontinued
     * @param unauthorizedRetry how long to wait after the gateway refused the key
     * @param replacedRetry     how long to wait after another connection with the same node uid replaced this one
     * @param backoff           the waits between other attempts
     */
    public record Timing(
            Duration pingInterval,
            Duration silenceLimit,
            Duration noKeyWait,
            Duration discontinuedRetry,
            Duration unauthorizedRetry,
            Duration replacedRetry,
            Backoff backoff) {
        /**
         * The times outside of tests: pings every 30 seconds, 75 seconds of silence, a minute without a key, six hours
         * while discontinued, an hour after a refused key and five minutes after being replaced.
         *
         * @param backoff the waits between attempts
         * @return the times
         */
        public static Timing standard(Backoff backoff) {
            return new Timing(
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(75),
                    Duration.ofMinutes(1),
                    Duration.ofHours(6),
                    Duration.ofHours(1),
                    Duration.ofMinutes(5),
                    backoff);
        }
    }

    /**
     * How a connection ended.
     *
     * @param code   the close code the gateway sent, {@link #NO_CLOSE} without one
     * @param reason the reason the gateway gave, empty without one
     */
    public record Closed(int code, String reason) {}

    /**
     * The outcome of one connection.
     *
     * @param answered whether the gateway answered at all
     * @param closed   how it ended
     */
    private record Ended(boolean answered, Closed closed) {}

    /**
     * One websocket and what was heard on it.
     */
    private final class Socket implements WebSocket.Listener {
        private final CompletableFuture<Void> ended = new CompletableFuture<>();
        private final StringBuilder buffer = new StringBuilder();
        private volatile @Nullable WebSocket webSocket;
        private volatile Instant lastHeard = clock.instant();
        private volatile boolean answered;
        private volatile int closeCode = NO_CLOSE;
        private volatile String closeReason = "";
        private CompletableFuture<?> sending = CompletableFuture.completedFuture(null);

        void open(WebSocket socket) {
            webSocket = socket;
            lastHeard = clock.instant();
        }

        synchronized boolean send(String text) {
            var socket = webSocket;
            if (socket == null || ended.isDone()) return false;
            sending = sending.handle((ignored, error) -> null)
                    .thenCompose(ignored -> socket.sendText(text, true))
                    .whenComplete((ignored, error) -> {
                        if (error != null) abort();
                    });
            return true;
        }

        void close() {
            var socket = webSocket;
            if (socket != null && !socket.isOutputClosed()) {
                try {
                    socket.sendClose(WebSocket.NORMAL_CLOSURE, "closing")
                            .get(CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                } catch (ExecutionException | TimeoutException e) {
                    logger.log(Level.FINE, "The dashboard connection did not close cleanly.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            abort();
        }

        void abort() {
            var socket = webSocket;
            if (socket != null) socket.abort();
            ended.complete(null);
        }

        @Override
        public void onOpen(WebSocket socket) {
            socket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                var text = buffer.toString();
                buffer.setLength(0);
                received(this, text);
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket socket, ByteBuffer message) {
            lastHeard = clock.instant();
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            logger.fine("The dashboard gateway closed the connection with " + statusCode + ".");
            closeCode = statusCode;
            closeReason = reason == null ? "" : reason;
            ended.complete(null);
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            logger.fine("The dashboard connection failed: " + error.getClass().getSimpleName());
            ended.complete(null);
        }
    }
}
