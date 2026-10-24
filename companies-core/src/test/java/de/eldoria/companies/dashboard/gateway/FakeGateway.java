/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.gateway;

import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * A gateway for tests: a minimal websocket server on a local port that records what nodes send and lets the test
 * answer. It speaks just enough of RFC 6455 for the JDK client: the upgrade, text, ping and close frames.
 */
public final class FakeGateway implements AutoCloseable {
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ServerSocket server;
    private final JsonMapper mapper = Protocol.mapper();
    private final BlockingQueue<Message> received = new LinkedBlockingQueue<>();
    private final BlockingQueue<Connection> connections = new LinkedBlockingQueue<>();
    private final List<Connection> all = new CopyOnWriteArrayList<>();
    private volatile Connection current;

    private FakeGateway(ServerSocket server) {
        this.server = server;
        Thread.ofVirtual().name("fake gateway").start(this::accept);
    }

    /**
     * Starts the gateway on a free local port.
     *
     * @return the gateway
     * @throws IOException when no port is free
     */
    public static FakeGateway start() throws IOException {
        return new FakeGateway(new ServerSocket(0, 10, InetAddress.getLoopbackAddress()));
    }

    /**
     * @return the address nodes connect to
     */
    public URI uri() {
        return URI.create("ws://127.0.0.1:" + server.getLocalPort() + "/api/v1/servers/connect");
    }

    /**
     * Waits for the next node to connect.
     *
     * @param timeout how long to wait
     * @return the connection
     */
    public Connection nextConnection(Duration timeout) throws InterruptedException {
        var connection = connections.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (connection == null) fail("No node connected within " + timeout);
        return connection;
    }

    /**
     * Waits for the next message of a type, skipping pings and other messages.
     *
     * @param type    the type
     * @param timeout how long to wait
     * @param <T>     the type
     * @return the message
     */
    public <T extends Message> T next(Class<T> type, Duration timeout) throws InterruptedException {
        var end = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < end) {
            var message = received.poll(Math.max(1, (end - System.nanoTime()) / 1_000_000), TimeUnit.MILLISECONDS);
            if (type.isInstance(message)) return type.cast(message);
        }
        return fail("No " + type.getSimpleName() + " within " + timeout);
    }

    /**
     * Sends a message on the latest connection.
     *
     * @param message the message
     */
    public void send(Message message) throws IOException {
        current.send(mapper.writeValueAsString(message));
    }

    /**
     * @return every connection so far
     */
    public List<Connection> connections() {
        return all;
    }

    @Override
    public void close() throws IOException {
        server.close();
        for (var connection : all) connection.close();
    }

    private void accept() {
        while (!server.isClosed()) {
            try {
                var socket = server.accept();
                var connection = new Connection(socket);
                connection.handshake();
                current = connection;
                all.add(connection);
                connections.add(connection);
                Thread.ofVirtual().start(connection::read);
            } catch (IOException e) {
                return;
            }
        }
    }

    /**
     * One node's connection.
     */
    public final class Connection {
        private final Socket socket;
        private final Map<String, String> headers = new HashMap<>();
        private final OutputStream out;
        private volatile boolean closed;

        Connection(Socket socket) throws IOException {
            this.socket = socket;
            out = socket.getOutputStream();
        }

        /**
         * A header of the upgrade request.
         *
         * @param name the name in any case
         * @return the value, null when absent
         */
        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        /**
         * @return true once the node or the gateway closed the connection
         */
        public boolean closed() {
            return closed;
        }

        private void handshake() throws IOException {
            var in = socket.getInputStream();
            var request = new ByteArrayOutputStream();
            while (!request.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n")) {
                var read = in.read();
                if (read < 0) throw new IOException("closed during the handshake");
                request.write(read);
            }
            var lines = request.toString(StandardCharsets.ISO_8859_1).split("\r\n");
            for (var i = 1; i < lines.length; i++) {
                var colon = lines[i].indexOf(':');
                if (colon > 0) {
                    headers.put(
                            lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                            lines[i].substring(colon + 1).trim());
                }
            }
            var accept = accept(header("Sec-WebSocket-Key"));
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                            + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        }

        private void read() {
            try (var in = new DataInputStream(socket.getInputStream())) {
                var text = new ByteArrayOutputStream();
                while (!closed) {
                    var first = in.readUnsignedByte();
                    var second = in.readUnsignedByte();
                    var opcode = first & 0x0f;
                    long length = second & 0x7f;
                    if (length == 126) length = in.readUnsignedShort();
                    else if (length == 127) length = in.readLong();
                    var mask = new byte[4];
                    if ((second & 0x80) != 0) in.readFully(mask);
                    var payload = new byte[(int) length];
                    in.readFully(payload);
                    for (var i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
                    switch (opcode) {
                        case 0x0, 0x1 -> {
                            text.write(payload);
                            if ((first & 0x80) != 0) {
                                received.add(mapper.readValue(text.toString(StandardCharsets.UTF_8), Message.class));
                                text.reset();
                            }
                        }
                        case 0x8 -> {
                            frame(0x8, payload);
                            close();
                        }
                        case 0x9 -> frame(0xA, payload);
                        default -> {}
                    }
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        /**
         * Sends a text message.
         *
         * @param text the text
         */
        public void send(String text) throws IOException {
            frame(0x1, text.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Closes the connection from the gateway's side.
         */
        public void close() {
            close(1000, "");
        }

        /**
         * Closes the connection from the gateway's side with a code and reason.
         *
         * @param code   the close code
         * @param reason the reason
         */
        public void close(int code, String reason) {
            try {
                var text = reason.getBytes(StandardCharsets.UTF_8);
                var payload = new byte[text.length + 2];
                payload[0] = (byte) (code >>> 8);
                payload[1] = (byte) code;
                System.arraycopy(text, 0, payload, 2, text.length);
                if (!closed) frame(0x8, payload);
            } catch (IOException ignored) {
                closed = true;
            }
            closed = true;
            try {
                socket.close();
            } catch (IOException ignored) {
                closed = true;
            }
        }

        private synchronized void frame(int opcode, byte[] payload) throws IOException {
            out.write(0x80 | opcode);
            if (payload.length < 126) {
                out.write(payload.length);
            } else {
                out.write(126);
                out.write(payload.length >>> 8);
                out.write(payload.length & 0xff);
            }
            out.write(payload);
            out.flush();
        }
    }

    private static String accept(String key) {
        try {
            var digest = MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
