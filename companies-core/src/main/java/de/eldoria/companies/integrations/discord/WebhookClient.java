/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Queues embeds and posts them to the webhook from one virtual thread.
 * <p>
 * {@link #enqueue(Embed)} never blocks: when the queue is full the oldest embed is dropped. The sending thread posts up
 * to ten embeds per request and no more than Discord's 6000 characters. It keeps to a budget of requests per minute
 * and to the rate limit Discord answers with: the {@code X-RateLimit-Remaining} and {@code X-RateLimit-Reset-After}
 * headers and the {@code retry_after} of a 429. Server errors and failed connections are retried three times after
 * 2, 4 and 8 seconds, then the embeds are dropped. A 400 is logged with Discord's answer and dropped. A 401, 403 or 404
 * means the webhook is gone, so it is disabled until the next reload. Dropped embeds are reported at most once a
 * minute. No log line contains the webhook url.
 */
public final class WebhookClient {
    static final int MAX_EMBEDS = 10;
    static final int MAX_CHARACTERS = 6000;
    static final int MAX_RETRIES = 3;
    static final int MAX_RATE_LIMITED = 5;
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(2);
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(1);
    private static final Duration DROP_REPORT_INTERVAL = Duration.ofMinutes(1);
    private static final long POLL_MILLIS = 250;
    private static final int BODY_LOG_LIMIT = 500;
    private static final Pattern RETRY_AFTER = Pattern.compile("\"retry_after\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    private final WebhookSender sender;
    private final WebhookPayload payload;
    private final WebhookRedaction redaction;
    private final InstantSource clock;
    private final Sleeper sleeper;
    private final Logger logger;
    private final RequestBudget budget;
    private final LinkedBlockingDeque<Embed> queue;
    private final AtomicInteger dropped = new AtomicInteger();
    private volatile boolean disabled;
    private volatile boolean closing;
    private Instant lastDropReport = Instant.EPOCH;
    private volatile String lastDropReason = "the queue was full";
    private Thread thread;

    /**
     * @param sender    sends the requests
     * @param payload   writes the request bodies
     * @param redaction hides the webhook url in log lines
     * @param limits    queue size and requests per minute
     * @param clock     source of the current time
     * @param sleeper   waits on the sending thread
     * @param logger    logger for failures
     */
    public WebhookClient(
            WebhookSender sender,
            WebhookPayload payload,
            WebhookRedaction redaction,
            Limits limits,
            InstantSource clock,
            Sleeper sleeper,
            Logger logger) {
        this.sender = sender;
        this.payload = payload;
        this.redaction = redaction;
        this.clock = clock;
        this.sleeper = sleeper;
        this.logger = logger;
        budget = new RequestBudget(limits.perMinute(), clock);
        queue = new LinkedBlockingDeque<>(Math.max(1, limits.queueSize()));
    }

    /**
     * Queues an embed. Never blocks; drops the oldest embed when the queue is full.
     *
     * @param embed the embed to post
     * @return false when the webhook is disabled or closing and the embed was not queued
     */
    public boolean enqueue(Embed embed) {
        if (disabled || closing) return false;
        synchronized (queue) {
            while (!queue.offerLast(embed)) {
                if (queue.pollFirst() == null) continue;
                lastDropReason = "the queue was full";
                dropped.incrementAndGet();
            }
        }
        return true;
    }

    /**
     * Starts the sending thread.
     */
    public synchronized void start() {
        if (thread != null) return;
        thread = Thread.ofVirtual().name("Companies Discord webhook").start(this::run);
    }

    /**
     * Stops taking embeds and sends what is queued until the time is up, then stops the sending thread.
     *
     * @param flush how long the queued embeds may take
     */
    public void stop(Duration flush) {
        closing = true;
        Thread running;
        synchronized (this) {
            running = thread;
        }
        if (running != null) await(running, flush);
        reportDrops(true);
    }

    private static void await(Thread running, Duration flush) {
        try {
            if (!running.join(flush)) {
                running.interrupt();
                running.join(Duration.ofSeconds(1));
            }
        } catch (InterruptedException e) {
            running.interrupt();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Whether the webhook was disabled because Discord no longer knows it.
     *
     * @return true when disabled
     */
    public boolean disabled() {
        return disabled;
    }

    /**
     * Embeds dropped and not yet reported.
     *
     * @return dropped embeds
     */
    int droppedCount() {
        return dropped.get();
    }

    private void run() {
        try {
            while (!(closing && queue.isEmpty())) {
                var batch = nextBatch();
                if (!batch.isEmpty()) deliver(batch);
                reportDrops(false);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!queue.isEmpty()) lastDropReason = "the plugin stopped before they were sent";
        dropped.addAndGet(queue.size());
        queue.clear();
    }

    /**
     * Takes the next embeds that fit into one request, waiting a short while for the first one.
     *
     * @return the embeds, empty when none arrived
     * @throws InterruptedException when the thread is interrupted while waiting
     */
    List<Embed> nextBatch() throws InterruptedException {
        List<Embed> batch = new ArrayList<>();
        var first = queue.pollFirst(POLL_MILLIS, TimeUnit.MILLISECONDS);
        if (first == null) return batch;
        batch.add(first);
        var characters = first.length();
        synchronized (queue) {
            var next = queue.peekFirst();
            while (next != null && batch.size() < MAX_EMBEDS && characters + next.length() <= MAX_CHARACTERS) {
                batch.add(queue.pollFirst());
                characters += next.length();
                next = queue.peekFirst();
            }
        }
        return batch;
    }

    /**
     * Posts the embeds in one request, waiting for the budget and retrying as described on the class.
     *
     * @param batch embeds of one request
     * @throws InterruptedException when the thread is interrupted while waiting
     */
    void deliver(List<Embed> batch) throws InterruptedException {
        var body = payload.write(batch);
        var failures = 0;
        var rateLimited = 0;
        while (!disabled) {
            var wait = budget.waitTime();
            if (!wait.isZero()) sleeper.sleep(wait);
            budget.take();
            Optional<WebhookResponse> answer = post(body);
            answer.ifPresent(this::respectRateLimit);
            var status = answer.map(WebhookResponse::status).orElse(0);
            if (status >= 200 && status < 300) return;
            if (status == 429) {
                if (++rateLimited > MAX_RATE_LIMITED) {
                    drop(batch, "Discord kept rate limiting the webhook");
                    return;
                }
                budget.pause(retryAfter(answer.get()));
                continue;
            }
            if (status == 0 || status >= 500) {
                if (++failures > MAX_RETRIES) {
                    drop(batch, "the webhook could not be reached after " + MAX_RETRIES + " retries");
                    return;
                }
                sleeper.sleep(FIRST_BACKOFF.multipliedBy(1L << (failures - 1)));
                continue;
            }
            if (status == 401 || status == 403 || status == 404) {
                disabled = true;
                drop(batch, "the webhook is disabled");
                logger.warning("Discord answered " + status
                        + " to the webhook, so it no longer exists or is not allowed. Webhook messages are disabled "
                        + "until the next reload.");
                return;
            }
            logger.warning("Discord refused a webhook message with " + status + ": " + bodyOf(answer.get()));
            drop(batch, "refused with " + status);
            return;
        }
        drop(batch, "the webhook is disabled");
    }

    private Optional<WebhookResponse> post(String body) throws InterruptedException {
        try {
            return Optional.of(sender.post(body));
        } catch (IOException e) {
            logger.fine(() -> "Webhook request failed: " + e.getClass().getSimpleName() + " "
                    + redaction.redact(String.valueOf(e.getMessage())));
            return Optional.empty();
        }
    }

    private void respectRateLimit(WebhookResponse response) {
        var remaining = response.header("X-RateLimit-Remaining");
        if (remaining.isEmpty() || !"0".equals(remaining.get().trim())) return;
        response.header("X-RateLimit-Reset-After")
                .flatMap(WebhookClient::seconds)
                .ifPresent(budget::pause);
    }

    private static Duration retryAfter(WebhookResponse response) {
        var matcher = RETRY_AFTER.matcher(response.body());
        if (matcher.find()) return seconds(matcher.group(1)).orElse(DEFAULT_RETRY_AFTER);
        return response.header("Retry-After").flatMap(WebhookClient::seconds).orElse(DEFAULT_RETRY_AFTER);
    }

    private static Optional<Duration> seconds(String value) {
        try {
            var seconds = Double.parseDouble(value.trim());
            if (seconds < 0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) return Optional.empty();
            return Optional.of(Duration.ofMillis((long) Math.ceil(seconds * 1000)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String bodyOf(WebhookResponse response) {
        return DiscordText.cut(redaction.redact(response.body()), BODY_LOG_LIMIT);
    }

    private void drop(List<Embed> batch, String reason) {
        lastDropReason = reason;
        dropped.addAndGet(batch.size());
    }

    private void reportDrops(boolean now) {
        var current = clock.instant();
        if (!now && Duration.between(lastDropReport, current).compareTo(DROP_REPORT_INTERVAL) < 0) return;
        var count = dropped.getAndSet(0);
        if (count == 0) return;
        lastDropReport = current;
        logger.warning(count + " webhook messages dropped, last because " + lastDropReason + ".");
    }

    /**
     * The limits of the client.
     *
     * @param queueSize embeds waiting at most
     * @param perMinute requests per minute at most
     */
    public record Limits(int queueSize, int perMinute) {}
}
