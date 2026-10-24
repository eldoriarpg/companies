/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.node;

import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.handshake.DeprecatedPart;
import de.eldoria.companies.web.api.handshake.Hello;
import de.eldoria.companies.web.api.handshake.Limits;
import de.eldoria.companies.web.api.handshake.NodeRole;
import de.eldoria.companies.web.api.handshake.VersionStatus;
import de.eldoria.companies.web.api.link.ErrorMessage;
import de.eldoria.companies.web.gateway.Socket;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * One node's connection: its state in the handshake, what it said in its hello, its patch sequence, and the requests
 * the gateway waits on. Requests carry ids {@code g-<n>}; the node's answer, or its error, carries the same id.
 */
public final class NodeLink {
    private static final JsonMapper MAPPER = Protocol.mapper();
    private final String socketId;
    private final Socket socket;
    private final String address;
    private final Instant connected;
    private final AtomicLong ids = new AtomicLong();
    private final Map<String, CompletableFuture<Message>> pending = new ConcurrentHashMap<>();
    private volatile State state = State.AWAITING_HELLO;
    private volatile Instant lastHeard;
    private volatile @Nullable Handshake handshake;
    private long lastSeq = -1;

    /**
     * Where a connection stands.
     */
    public enum State {
        /**
         * Connected, no hello yet.
         */
        AWAITING_HELLO,
        /**
         * Welcomed; requests and patches flow.
         */
        READY,
        /**
         * Welcomed with a discontinued version; only status messages are accepted.
         */
        DISCONTINUED,
        /**
         * Closed.
         */
        CLOSED
    }

    /**
     * How a patch's sequence number relates to the node's previous one.
     */
    public enum Sequence {
        /**
         * The next number, or the first one seen.
         */
        NEXT,
        /**
         * Numbers were skipped: patches were lost.
         */
        GAP,
        /**
         * Not newer than the last one: a repeat.
         */
        STALE
    }

    /**
     * What the gateway agreed on with the node.
     *
     * @param serverId the server the node belongs to
     * @param hello the node's hello
     * @param protocol the protocol version used
     * @param features the features both the node and the gateway support
     * @param status the state of the node's plugin version
     * @param deprecated the deprecated parts the node still uses
     */
    public record Handshake(
            String serverId,
            Hello hello,
            int protocol,
            Set<String> features,
            VersionStatus status,
            List<DeprecatedPart> deprecated) {
        /**
         * @return the node's uid
         */
        public UUID uid() {
            return hello.node().uid();
        }

        /**
         * @return the node's role
         */
        public NodeRole role() {
            return hello.node().role();
        }
    }

    /**
     * @param socketId the websocket's id
     * @param socket the websocket
     * @param address the client address it comes from
     * @param now the time it connected
     */
    public NodeLink(String socketId, Socket socket, String address, Instant now) {
        this.socketId = socketId;
        this.socket = socket;
        this.address = address;
        this.connected = now;
        this.lastHeard = now;
    }

    /**
     * @return the websocket's id
     */
    public String socketId() {
        return socketId;
    }

    /**
     * @return the client address the connection comes from
     */
    public String address() {
        return address;
    }

    /**
     * @return when the connection was opened
     */
    public Instant connected() {
        return connected;
    }

    /**
     * @return where the connection stands
     */
    public State state() {
        return state;
    }

    /**
     * @return the handshake, none before the welcome
     */
    public @Nullable Handshake handshake() {
        return handshake;
    }

    /**
     * @return the handshake
     * @throws IllegalStateException before the welcome
     */
    public Handshake agreed() {
        Handshake agreed = handshake;
        if (agreed == null) throw new IllegalStateException("The node has not been welcomed");
        return agreed;
    }

    /**
     * @return the most entries the node returns in one page
     */
    public int maxPageSize() {
        Limits limits = agreed().hello().limits();
        return limits == null || limits.maxPageSize() <= 0 ? 50 : limits.maxPageSize();
    }

    /**
     * Records the handshake and the state it leads to.
     *
     * @param agreed the handshake
     * @param state {@link State#READY} or {@link State#DISCONTINUED}
     */
    void welcomed(Handshake agreed, State state) {
        this.handshake = agreed;
        this.state = state;
    }

    /**
     * @return when the node was last heard from
     */
    public Instant lastHeard() {
        return lastHeard;
    }

    /**
     * @param now the time a message of the node arrived
     */
    void heard(Instant now) {
        lastHeard = now;
    }

    /**
     * Sends a message.
     *
     * @param message the message
     */
    public void send(Message message) {
        socket.send(MAPPER.writeValueAsString(message));
    }

    /**
     * Sends a request and waits for its answer asynchronously.
     *
     * @param build builds the request from its id
     * @param timeout how long to wait for the answer
     * @return the answer; fails with {@link NodeRequestException} on an error, a timeout or a closed connection
     */
    public CompletableFuture<Message> request(Function<String, Message> build, Duration timeout) {
        String id = "g-" + ids.incrementAndGet();
        CompletableFuture<Message> answer = new CompletableFuture<>();
        if (state == State.CLOSED) {
            answer.completeExceptionally(new NodeRequestException(null, "The server is not connected"));
            return answer;
        }
        pending.put(id, answer);
        answer.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((ignored, error) -> pending.remove(id));
        try {
            send(build.apply(id));
        } catch (RuntimeException e) {
            answer.completeExceptionally(new NodeRequestException(null, "The request could not be sent"));
        }
        return answer;
    }

    /**
     * @return a fresh id for a message the gateway sends without waiting for its answer
     */
    public String nextId() {
        return "g-" + ids.incrementAndGet();
    }

    /**
     * Completes the request an answer belongs to.
     *
     * @param answer a message from the node
     * @return whether it answered a request of the gateway
     */
    boolean answer(Message answer) {
        String id = answer.id();
        if (id == null) return false;
        CompletableFuture<Message> request = pending.remove(id);
        if (request == null) return false;
        if (answer instanceof ErrorMessage error) {
            String message = error.message();
            request.completeExceptionally(new NodeRequestException(
                    error.code(), message == null ? error.code().name() : message));
        } else {
            request.complete(answer);
        }
        return true;
    }

    /**
     * Records a patch's sequence number.
     *
     * @param seq the number
     * @return how it relates to the previous one
     */
    public synchronized Sequence sequence(long seq) {
        if (lastSeq >= 0 && seq <= lastSeq) return Sequence.STALE;
        boolean gap = lastSeq >= 0 && seq != lastSeq + 1;
        lastSeq = seq;
        return gap ? Sequence.GAP : Sequence.NEXT;
    }

    /**
     * Closes the connection and fails every request still waiting.
     *
     * @param code the close code
     * @param reason the close reason
     */
    public void close(int code, String reason) {
        closed();
        socket.close(code, reason);
    }

    /**
     * Marks the connection closed and fails every request still waiting.
     */
    void closed() {
        state = State.CLOSED;
        for (CompletableFuture<Message> request : List.copyOf(pending.values())) {
            request.completeExceptionally(new NodeRequestException(null, "The server disconnected"));
        }
        pending.clear();
    }
}
