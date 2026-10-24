/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.browser;

import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.gateway.Socket;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The open browser streams, by session. A browser pings every 20 seconds; a stream silent for
 * {@value #IDLE_SECONDS} seconds is closed.
 */
public final class BrowserStreams {
    /**
     * Close code when the session ended or the cookie is not valid.
     */
    public static final int CLOSE_SESSION_ENDED = 4401;

    /**
     * Close code when the server's plugin version is discontinued on the dashboard.
     */
    public static final int CLOSE_DISCONTINUED = 4403;

    private static final long IDLE_SECONDS = 60;
    private static final JsonMapper MAPPER = Protocol.mapper();
    private final Map<String, Stream> streams = new ConcurrentHashMap<>();
    private final Clock clock;

    private static final class Stream {
        private final String sessionId;
        private final String serverId;
        private final Socket socket;
        private volatile Instant lastHeard;

        private Stream(String sessionId, String serverId, Socket socket, Instant now) {
            this.sessionId = sessionId;
            this.serverId = serverId;
            this.socket = socket;
            this.lastHeard = now;
        }
    }

    /**
     * @param clock the time
     */
    public BrowserStreams(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param socketId the websocket's id
     * @param sessionId the session it belongs to
     * @param serverId the session's server
     * @param socket the websocket
     */
    public void open(String socketId, String sessionId, String serverId, Socket socket) {
        streams.put(socketId, new Stream(sessionId, serverId, socket, clock.instant()));
    }

    /**
     * @param socketId the websocket's id
     * @return the session the stream belongs to, none for an unknown stream
     */
    public Optional<String> heard(String socketId) {
        Stream stream = streams.get(socketId);
        if (stream == null) return Optional.empty();
        stream.lastHeard = clock.instant();
        return Optional.of(stream.sessionId);
    }

    /**
     * @param socketId the websocket's id
     */
    public void closed(String socketId) {
        streams.remove(socketId);
    }

    /**
     * @param socketId the websocket's id
     * @param message the message for that stream only
     */
    public void reply(String socketId, Object message) {
        Stream stream = streams.get(socketId);
        if (stream != null) stream.socket.send(MAPPER.writeValueAsString(message));
    }

    /**
     * @param sessionId a session
     * @param message the message for every stream of it
     */
    public void push(String sessionId, Object message) {
        String text = MAPPER.writeValueAsString(message);
        for (Stream stream : List.copyOf(streams.values())) {
            if (stream.sessionId.equals(sessionId)) stream.socket.send(text);
        }
    }

    /**
     * @param serverId a server
     * @param message the message for every stream of its sessions
     */
    public void pushServer(String serverId, Object message) {
        String text = MAPPER.writeValueAsString(message);
        for (Stream stream : List.copyOf(streams.values())) {
            if (stream.serverId.equals(serverId)) stream.socket.send(text);
        }
    }

    /**
     * Sends a last message to every stream of a session and closes them.
     *
     * @param sessionId the session
     * @param message the last message, such as {@code session.closed}
     * @param code the close code
     */
    public void end(String sessionId, Object message, int code) {
        String text = MAPPER.writeValueAsString(message);
        streams.entrySet().removeIf(entry -> {
            Stream stream = entry.getValue();
            if (!stream.sessionId.equals(sessionId)) return false;
            stream.socket.send(text);
            stream.socket.close(code, code == CLOSE_DISCONTINUED ? "Discontinued" : "Session ended");
            return true;
        });
    }

    /**
     * Closes streams whose browser stopped pinging.
     */
    public void sweep() {
        Instant limit = clock.instant().minus(Duration.ofSeconds(IDLE_SECONDS));
        streams.entrySet().removeIf(entry -> {
            if (!entry.getValue().lastHeard.isBefore(limit)) return false;
            entry.getValue().socket.close(1001, "Idle");
            return true;
        });
    }

    /**
     * Closes every stream, when the gateway stops.
     */
    public void closeAll() {
        streams.values().forEach(stream -> stream.socket.close(1001, "The gateway is stopping"));
        streams.clear();
    }
}
