/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.api.Features;
import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.handshake.Branding;
import de.eldoria.companies.web.api.handshake.Credentials;
import de.eldoria.companies.web.api.handshake.Hello;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.handshake.Limits;
import de.eldoria.companies.web.api.handshake.NodeInfo;
import de.eldoria.companies.web.api.handshake.NodeRole;
import de.eldoria.companies.web.api.handshake.Welcome;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A plugin node for tests: a websocket client on {@code /api/v1/servers/connect} that sends protocol messages and
 * reads the gateway's.
 */
final class FakeNode implements AutoCloseable {
    static final List<String> FEATURES =
            List.of(Features.ORDERS_BROWSE, Features.COMPANY_MEMBERS, Features.INBOX, Features.RANKINGS);
    private static final JsonMapper MAPPER = Protocol.mapper();
    private final LinkedBlockingQueue<String> inbox = new LinkedBlockingQueue<>();
    private final CompletableFuture<Integer> closed = new CompletableFuture<>();
    private final WebSocket socket;

    private FakeNode(TestGateway gateway, String address) {
        this.socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .header("X-Forwarded-For", address)
                .header("User-Agent", "Companies/2.2.0")
                .buildAsync(gateway.ws(Routes.CONNECT), new WebSocket.Listener() {
                    private final StringBuilder partial = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        partial.append(data);
                        if (last) {
                            inbox.add(partial.toString());
                            partial.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        closed.complete(statusCode);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        closed.complete(-1);
                    }
                })
                .join();
    }

    static FakeNode connect(TestGateway gateway) {
        return new FakeNode(gateway, "198.51.100.1");
    }

    static Hello hello(KeyKind kind, String key, UUID node, String plugin) {
        return hello(kind, key, node, NodeRole.PRIMARY, plugin, FEATURES, null);
    }

    static Hello hello(
            KeyKind kind,
            String key,
            UUID node,
            NodeRole role,
            String plugin,
            List<String> features,
            @Nullable Branding branding) {
        return new Hello(
                "1",
                Protocol.VERSION,
                plugin,
                "1.9",
                new NodeInfo(node, role),
                features,
                new Limits(50),
                new Credentials(kind, key),
                "Companies/" + plugin,
                branding);
    }

    /**
     * Connects and is welcomed.
     */
    static FakeNode welcomed(TestGateway gateway, Hello hello) {
        FakeNode node = connect(gateway);
        node.send(hello);
        node.expect(Welcome.class);
        return node;
    }

    void send(Message message) {
        sendRaw(MAPPER.writeValueAsString(message));
    }

    void sendRaw(String text) {
        socket.sendText(text, true).join();
    }

    /**
     * Sends a text the gateway may refuse while it is still being written, as it closes the socket once it has read
     * more than it allows.
     *
     * @return whether the whole text was written; when not, the gateway closed the socket during the write
     */
    boolean sendRefusable(String text) {
        try {
            socket.sendText(text, true).join();
            return true;
        } catch (CompletionException e) {
            if (e.getCause() instanceof IOException) return false;
            throw e;
        }
    }

    boolean outputClosed() {
        return socket.isOutputClosed();
    }

    Message next() {
        String text = poll(Duration.ofSeconds(10));
        assertNotNull(text, "The gateway sent nothing");
        return MAPPER.readValue(text, Message.class);
    }

    <T extends Message> T expect(Class<T> type) {
        return assertInstanceOf(type, next());
    }

    void assertQuiet() {
        String text = poll(Duration.ofMillis(300));
        assertNull(text, "The gateway sent " + text);
    }

    /**
     * Asserts the gateway sends nothing but messages of one type for a moment.
     */
    void assertQuietBut(Class<? extends Message> allowed) {
        String text;
        while ((text = poll(Duration.ofMillis(300))) != null) {
            assertInstanceOf(allowed, MAPPER.readValue(text, Message.class), "The gateway sent " + text);
        }
    }

    int awaitClose() {
        return closed.orTimeout(10, TimeUnit.SECONDS).join();
    }

    private @Nullable String poll(Duration timeout) {
        try {
            return inbox.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public void close() {
        if (!socket.isOutputClosed()) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").exceptionally(error -> socket);
        }
        closed.completeOnTimeout(-1, 5, TimeUnit.SECONDS).join();
    }
}
