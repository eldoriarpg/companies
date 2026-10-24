/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookClientTest {
    private static final String TOKEN = "s3cretT0ken";
    private static final String URL = "https://discord.com/api/webhooks/42/" + TOKEN;

    private FakeTime time;
    private FakeSender sender;
    private List<String> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        time = new FakeTime();
        sender = new FakeSender();
        logs = new ArrayList<>();
        logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logs.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
    }

    private WebhookClient client(int queueSize, int perMinute) {
        return new WebhookClient(
                sender,
                new WebhookPayload(Optional.of("Companies"), Optional.empty()),
                new WebhookRedaction(URL),
                new WebhookClient.Limits(queueSize, perMinute),
                time,
                time,
                logger);
    }

    private static Embed embed(String title) {
        return new Embed(title, "", 0, List.of(), Instant.EPOCH);
    }

    private static WebhookResponse response(int status) {
        return new WebhookResponse(status, "", Map.of());
    }

    @Test
    void batchesAtMostTenEmbeds() throws InterruptedException {
        var client = client(100, 60);
        for (var i = 0; i < 25; i++) client.enqueue(embed("e" + i));

        assertEquals(10, client.nextBatch().size());
        assertEquals(10, client.nextBatch().size());
        assertEquals(5, client.nextBatch().size());
    }

    @Test
    void batchKeepsToCharacterLimit() throws InterruptedException {
        var client = client(100, 60);
        var big = new Embed(
                "t", "x".repeat(3000), 0, List.of(new Embed.Field("f", "y".repeat(1000), false)), Instant.EPOCH);
        client.enqueue(big);
        client.enqueue(big);

        assertEquals(1, client.nextBatch().size());
        assertEquals(1, client.nextBatch().size());
    }

    @Test
    void fullQueueDropsOldest() throws InterruptedException {
        var client = client(2, 60);
        client.enqueue(embed("first"));
        client.enqueue(embed("second"));
        client.enqueue(embed("third"));

        var batch = client.nextBatch();
        assertEquals(
                List.of("second", "third"), batch.stream().map(Embed::title).toList());
        assertEquals(1, client.droppedCount());
    }

    @Test
    void successSendsOnceWithMentionsDisabled() throws InterruptedException {
        sender.answers.add(response(204));
        var client = client(10, 60);

        client.deliver(List.of(embed("one")));

        assertEquals(1, sender.bodies.size());
        assertTrue(sender.bodies.getFirst().contains("\"allowed_mentions\":{\"parse\":[]}"));
        assertEquals(List.of(), time.sleeps);
    }

    @Test
    void rateLimitedRequestWaitsRetryAfter() throws InterruptedException {
        sender.answers.add(new WebhookResponse(429, "{\"message\":\"slow\",\"retry_after\":1.5}", Map.of()));
        sender.answers.add(response(204));
        var client = client(10, 60);

        client.deliver(List.of(embed("one")));

        assertEquals(2, sender.bodies.size());
        assertEquals(List.of(Duration.ofMillis(1500)), time.sleeps);
    }

    @Test
    void exhaustedBucketWaitsForHeaderReset() throws InterruptedException {
        sender.answers.add(
                new WebhookResponse(204, "", Map.of("X-RateLimit-Remaining", "0", "X-RateLimit-Reset-After", "2")));
        sender.answers.add(response(204));
        var client = client(10, 60);

        client.deliver(List.of(embed("one")));
        client.deliver(List.of(embed("two")));

        assertEquals(List.of(Duration.ofSeconds(2)), time.sleeps);
    }

    @Test
    void budgetLimitsRequestsPerMinute() throws InterruptedException {
        for (var i = 0; i < 3; i++) sender.answers.add(response(204));
        var client = client(10, 2);

        client.deliver(List.of(embed("one")));
        client.deliver(List.of(embed("two")));
        client.deliver(List.of(embed("three")));

        assertEquals(List.of(Duration.ofSeconds(30)), time.sleeps);
    }

    @Test
    void serverErrorsAreRetriedThreeTimesWithBackoff() throws InterruptedException {
        sender.answers.add(response(500));
        sender.answers.add(response(502));
        sender.fail(new ConnectException("Connection refused to " + URL));
        sender.answers.add(response(503));
        var client = client(10, 60);

        client.deliver(List.of(embed("one")));

        assertEquals(4, sender.calls);
        assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8)), time.sleeps);
        assertEquals(1, client.droppedCount());
        assertFalse(client.disabled());
    }

    @Test
    void retryAfterServerErrorSucceeds() throws InterruptedException {
        sender.answers.add(response(500));
        sender.answers.add(response(204));
        var client = client(10, 60);

        client.deliver(List.of(embed("one")));

        assertEquals(2, sender.calls);
        assertEquals(0, client.droppedCount());
    }

    @Test
    void missingWebhookDisablesClient() throws InterruptedException {
        sender.answers.add(response(404));
        var client = client(10, 60);

        client.deliver(List.of(embed("one")));

        assertTrue(client.disabled());
        assertFalse(client.enqueue(embed("two")));
        assertEquals(1, sender.calls);
        assertEquals(1, logs.size());
    }

    @Test
    void badRequestIsLoggedOnceAndDropped() throws InterruptedException {
        sender.answers.add(new WebhookResponse(400, "{\"embeds\":[\"bad\"],\"url\":\"" + URL + "\"}", Map.of()));
        var client = client(10, 60);

        client.deliver(List.of(embed("one")));

        assertEquals(1, sender.calls);
        assertEquals(1, logs.size());
        assertTrue(logs.getFirst().contains("400"));
        assertFalse(client.disabled());
    }

    @Test
    void logsNeverContainTheUrl() throws InterruptedException {
        sender.answers.add(new WebhookResponse(400, "bad " + URL, Map.of()));
        for (var i = 0; i < 4; i++) sender.fail(new IOException("failed " + URL));
        sender.answers.add(response(401));
        var client = client(10, 60);

        client.deliver(List.of(embed("one")));
        client.deliver(List.of(embed("two")));
        client.deliver(List.of(embed("three")));
        client.stop(Duration.ZERO);

        assertEquals(6, sender.calls);
        assertTrue(client.disabled());
        assertTrue(logs.size() >= 3, String.join("\n", logs));
        assertTrue(logs.stream().noneMatch(line -> line.contains(TOKEN)), String.join("\n", logs));
    }

    @Test
    void sendingThreadDeliversAndStops() {
        var real = new WebhookClient(
                sender,
                new WebhookPayload(Optional.empty(), Optional.empty()),
                new WebhookRedaction(URL),
                new WebhookClient.Limits(10, 60),
                InstantSource.system(),
                Sleeper.THREAD,
                logger);
        sender.answers.add(response(204));
        real.start();
        real.enqueue(embed("one"));

        real.stop(Duration.ofSeconds(3));

        assertEquals(1, sender.calls);
        assertFalse(real.enqueue(embed("late")));
    }

    private static final class FakeTime implements InstantSource, Sleeper {
        private final List<Duration> sleeps = new ArrayList<>();
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public void sleep(Duration duration) {
            sleeps.add(duration);
            now = now.plus(duration);
        }
    }

    private static final class FakeSender implements WebhookSender {
        private final Deque<Object> answers = new ArrayDeque<>();
        private final List<String> bodies = new ArrayList<>();
        private int calls;

        void fail(IOException failure) {
            answers.add(failure);
        }

        @Override
        public synchronized WebhookResponse post(String json) throws IOException {
            calls++;
            bodies.add(json);
            var answer = answers.isEmpty() ? response(204) : answers.pop();
            if (answer instanceof IOException failure) throw failure;
            return (WebhookResponse) answer;
        }
    }
}
