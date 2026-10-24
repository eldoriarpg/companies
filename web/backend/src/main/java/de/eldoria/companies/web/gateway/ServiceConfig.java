/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The gateway's settings, read from the environment. Every value has a default except the secrets.
 *
 * @param port HTTP port ({@code PORT}, 8080)
 * @param mode whether servers authenticate with Lyna dashboard keys or with the gateway's own server tokens
 * ({@code GATEWAY_MODE}, {@code hosted} or {@code self-hosted}; hosted)
 * @param publicUrl the address players open, without a trailing slash; links are this plus the short id
 * ({@code PUBLIC_URL}, {@code http://localhost:4000})
 * @param lyna how dashboard keys are verified; used in hosted mode only
 * @param trustedProxies proxies whose forwarded client address is believed, as addresses or ranges, comma separated
 * ({@code TRUSTED_PROXIES}); empty trusts none, see {@link ClientAddress}
 * @param cloudflare whether {@code CF-Connecting-IP} names the client when the request comes from a trusted proxy
 * ({@code CLOUDFLARE}, false)
 * @param turnstile the optional Turnstile check of the login form
 * @param sessionIdle how long a session lives without a browser using it ({@code SESSION_IDLE_MINUTES}, 60)
 * @param sessionMaxAge how long a session lives at most ({@code SESSION_MAX_HOURS}, 24)
 * @param serverGrace how long sessions survive while no node of their server is connected
 * ({@code SERVER_GRACE_MINUTES}, 10)
 * @param cacheIdle how long a snapshot stays in memory without a browser using it ({@code CACHE_IDLE_MINUTES}, 15)
 * @param loginsPerAddress login attempts one address may make per ten minutes ({@code LOGINS_PER_ADDRESS}, 10)
 * @param readsPerSession read requests one session may make per ten seconds ({@code READS_PER_SESSION}, 10)
 * @param writesPerSession actions that change something one session may make per ten seconds
 * ({@code WRITES_PER_SESSION}, 5)
 * @param connectsPerAddress server connections one address may open per minute ({@code CONNECTS_PER_ADDRESS}, 30)
 * @param requestTimeout how long the gateway waits for a node's answer ({@code REQUEST_TIMEOUT_SECONDS}, 10)
 * @param secureCookie whether the session cookie is marked {@code Secure} ({@code COOKIE_SECURE}, true)
 * @param versionPolicy a version policy file replacing the one shipped with the gateway ({@code VERSION_POLICY})
 * @param database the database connection
 */
public record ServiceConfig(
        int port,
        GatewayMode mode,
        String publicUrl,
        LynaConfig lyna,
        List<String> trustedProxies,
        boolean cloudflare,
        TurnstileConfig turnstile,
        Duration sessionIdle,
        Duration sessionMaxAge,
        Duration serverGrace,
        Duration cacheIdle,
        int loginsPerAddress,
        int readsPerSession,
        int writesPerSession,
        int connectsPerAddress,
        Duration requestTimeout,
        boolean secureCookie,
        @Nullable Path versionPolicy,
        DatabaseConfig database) {

    /**
     * How dashboard keys are verified with Lyna.
     *
     * @param url Lyna's address ({@code LYNA_URL}, {@code https://lyna.eldoria.de})
     * @param secret the gateway secret Lyna expects ({@code DASHBOARD_GATEWAYSECRET}); required in hosted mode
     * @param product Companies' product id at Lyna ({@code LYNA_PRODUCT}, 2)
     * @param cache how long an answer is used when Lyna cannot be reached ({@code LYNA_CACHE_MINUTES}, 5)
     */
    public record LynaConfig(URI url, String secret, int product, Duration cache) {
        @Override
        public String toString() {
            return "LynaConfig[url=%s, product=%d, cache=%s]".formatted(url, product, cache);
        }
    }

    /**
     * The optional Turnstile check of the login form, on when a secret is set.
     *
     * @param secret the secret key ({@code TURNSTILE_SECRET}); empty switches the check off
     * @param siteKey the site key the web app shows the challenge with ({@code TURNSTILE_SITEKEY})
     * @param url the verification endpoint ({@code TURNSTILE_URL}, Cloudflare's siteverify)
     */
    public record TurnstileConfig(String secret, String siteKey, URI url) {
        /**
         * @return whether logins need a Turnstile token
         */
        public boolean enabled() {
            return !secret.isBlank();
        }

        @Override
        public String toString() {
            return "TurnstileConfig[enabled=%s, siteKey=%s, url=%s]".formatted(enabled(), siteKey, url);
        }
    }

    /**
     * @param host {@code DB_HOST}, localhost
     * @param port {@code DB_PORT}, 5432
     * @param name {@code DB_NAME}, postgres
     * @param user {@code DB_USER}, postgres
     * @param password {@code DB_PASSWORD}, empty
     * @param schema {@code DB_SCHEMA}, companies_gateway
     */
    public record DatabaseConfig(String host, int port, String name, String user, String password, String schema) {
        @Override
        public String toString() {
            return "DatabaseConfig[host=%s, port=%d, name=%s, user=%s, schema=%s]"
                    .formatted(host, port, name, user, schema);
        }
    }

    /**
     * @param environment the environment, e.g. {@link System#getenv()}
     * @return the settings, defaults where a variable is missing
     */
    public static ServiceConfig of(Map<String, String> environment) {
        Function<String, @Nullable String> env = environment::get;
        String policy = text(env, "VERSION_POLICY", "");
        return new ServiceConfig(
                integer(env, "PORT", 8080),
                GatewayMode.parse(text(env, "GATEWAY_MODE", "hosted")),
                stripSlash(text(env, "PUBLIC_URL", "http://localhost:4000")),
                new LynaConfig(
                        URI.create(stripSlash(text(env, "LYNA_URL", "https://lyna.eldoria.de"))),
                        text(env, "DASHBOARD_GATEWAYSECRET", ""),
                        integer(env, "LYNA_PRODUCT", 2),
                        Duration.ofMinutes(integer(env, "LYNA_CACHE_MINUTES", 5))),
                list(env, "TRUSTED_PROXIES"),
                bool(env, "CLOUDFLARE", false),
                new TurnstileConfig(
                        text(env, "TURNSTILE_SECRET", ""),
                        text(env, "TURNSTILE_SITEKEY", ""),
                        URI.create(text(
                                env, "TURNSTILE_URL", "https://challenges.cloudflare.com/turnstile/v0/siteverify"))),
                Duration.ofMinutes(integer(env, "SESSION_IDLE_MINUTES", 60)),
                Duration.ofHours(integer(env, "SESSION_MAX_HOURS", 24)),
                Duration.ofMinutes(integer(env, "SERVER_GRACE_MINUTES", 10)),
                Duration.ofMinutes(integer(env, "CACHE_IDLE_MINUTES", 15)),
                integer(env, "LOGINS_PER_ADDRESS", 10),
                integer(env, "READS_PER_SESSION", 10),
                integer(env, "WRITES_PER_SESSION", 5),
                integer(env, "CONNECTS_PER_ADDRESS", 30),
                Duration.ofSeconds(integer(env, "REQUEST_TIMEOUT_SECONDS", 10)),
                bool(env, "COOKIE_SECURE", true),
                policy.isEmpty() ? null : Path.of(policy),
                new DatabaseConfig(
                        text(env, "DB_HOST", "localhost"),
                        integer(env, "DB_PORT", 5432),
                        text(env, "DB_NAME", "postgres"),
                        text(env, "DB_USER", "postgres"),
                        text(env, "DB_PASSWORD", ""),
                        text(env, "DB_SCHEMA", "companies_gateway")));
    }

    private static String text(Function<String, @Nullable String> env, String name, String fallback) {
        String value = env.apply(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static List<String> list(Function<String, @Nullable String> env, String name) {
        return Arrays.stream(text(env, name, "").split(","))
                .map(String::strip)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    private static int integer(Function<String, @Nullable String> env, String name, int fallback) {
        return Integer.parseInt(text(env, name, Integer.toString(fallback)));
    }

    private static boolean bool(Function<String, @Nullable String> env, String name, boolean fallback) {
        return switch (text(env, name, Boolean.toString(fallback)).toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> throw new IllegalArgumentException(name + " must be true or false");
        };
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
