/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.node;

import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.handshake.Credentials;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.gateway.Secrets;
import de.eldoria.companies.web.gateway.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Verifies Lyna dashboard keys for the hosted gateway: {@code POST {LYNA_URL}/api/v1/dashboard/key/verify} with the
 * gateway secret as bearer token and {@code {key}} as body. Lyna answers 200 with {@code valid}, and for a valid key
 * the product, the licence kind and the {@code keyId} that stays the same across rotations.
 * <p>
 * Every new connection is verified with Lyna. A positive answer is remembered for the cache time and used only when
 * Lyna cannot be reached, so a short Lyna outage does not lock out servers that just reconnect. The key is never
 * logged; the cache holds its hash.
 */
public final class LynaAuthenticator implements ServerAuthenticator {
    /**
     * The prefix of every dashboard key.
     */
    public static final String KEY_PREFIX = "lyd_";

    private static final Logger log = LoggerFactory.getLogger(LynaAuthenticator.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final JsonMapper MAPPER = Protocol.mapper();
    private final ServiceConfig.LynaConfig config;
    private final HttpClient client;
    private final Clock clock;
    private final Map<String, Remembered> remembered = new ConcurrentHashMap<>();
    private final AtomicLong rotated = new AtomicLong();

    /**
     * Lyna's answer to a verification.
     *
     * @param valid whether the key may be used
     * @param product the product the key is for
     * @param kind {@code BOUGHT} or {@code TRIAL}
     * @param keyId the key's id, stable across rotations
     * @param rotated whether the key was already traded for its successor and is inside the grace period
     * @param reason why it is not valid: {@code UNKNOWN}, {@code EXPIRED}, {@code NO_ACCESS} or {@code RETIRED}
     */
    record Verification(
            boolean valid,
            @Nullable Integer product,
            @Nullable String kind,
            @Nullable String keyId,
            boolean rotated,
            @Nullable String reason) {}

    private record Remembered(Verification verification, Instant at) {}

    /**
     * @param config where Lyna is and the gateway secret
     * @param client the HTTP client
     * @param clock the time
     */
    public LynaAuthenticator(ServiceConfig.LynaConfig config, HttpClient client, Clock clock) {
        this.config = config;
        this.client = client;
        this.clock = clock;
    }

    @Override
    public Authentication authenticate(Credentials credentials) {
        if (credentials.kind() != KeyKind.DASHBOARD_KEY) {
            return new Authentication.Refused(
                    ErrorCode.UNAUTHORIZED, "This gateway is hosted and needs a Lyna dashboard key");
        }
        String key = credentials.key();
        if (!key.startsWith(KEY_PREFIX)) return new Authentication.Refused(ErrorCode.UNAUTHORIZED, "UNKNOWN");
        String hash = Secrets.hash(key);
        Verification verification;
        try {
            verification = verify(key);
        } catch (Unavailable e) {
            log.warn("Lyna could not verify a dashboard key: {}", e.getMessage());
            Remembered known = remembered.get(hash);
            if (known == null || known.at().isBefore(clock.instant().minus(config.cache()))) {
                return new Authentication.Refused(ErrorCode.INTERNAL, "The key cannot be verified right now");
            }
            verification = known.verification();
        }
        return judge(hash, verification);
    }

    private Authentication judge(String hash, Verification verification) {
        String keyId = verification.keyId();
        if (!verification.valid() || keyId == null) {
            remembered.remove(hash);
            String reason = verification.reason();
            return new Authentication.Refused(ErrorCode.UNAUTHORIZED, reason == null ? "UNKNOWN" : reason);
        }
        Integer product = verification.product();
        if (product == null || product != config.product()) {
            remembered.remove(hash);
            return new Authentication.Refused(ErrorCode.UNAUTHORIZED, "The key is not a Companies dashboard key");
        }
        if (verification.rotated()) {
            long count = rotated.incrementAndGet();
            log.info(
                    "Key {} connected with its previous dashboard key inside the grace period ({} so far)",
                    keyId,
                    count);
        }
        Instant now = clock.instant();
        remembered.values().removeIf(entry -> entry.at().isBefore(now.minus(config.cache())));
        remembered.put(hash, new Remembered(verification, now));
        return new Authentication.Accepted("lyna:" + keyId, verification.kind());
    }

    /**
     * Connections made with a key that was already rotated, which happens when a node connects just after another
     * node of its network traded the key. Many of them mean nodes do not read the current key from the database.
     *
     * @return how many connections used a rotated key since the start
     */
    public long rotatedConnections() {
        return rotated.get();
    }

    private Verification verify(String key) throws Unavailable {
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.url() + "/api/v1/dashboard/key/verify"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + config.secret())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(Map.of("key", key))))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new Unavailable(e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unavailable("interrupted");
        }
        if (response.statusCode() == 401) {
            log.error("Lyna refused the gateway secret; check DASHBOARD_GATEWAYSECRET");
            throw new Unavailable("Lyna refused the gateway secret");
        }
        if (response.statusCode() != 200) throw new Unavailable("Lyna answered " + response.statusCode());
        try {
            return MAPPER.readValue(response.body(), Verification.class);
        } catch (JacksonException e) {
            throw new Unavailable("Lyna's answer is unreadable");
        }
    }

    /**
     * Lyna could not be asked or gave no usable answer.
     */
    private static final class Unavailable extends Exception {
        private static final long serialVersionUID = 1L;

        Unavailable(String message) {
            super(message);
        }
    }
}
