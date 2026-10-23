/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Resolves the configured webhook url.
 * <p>
 * The configured value is either the url itself or {@code env:NAME}, which reads it from the environment variable
 * {@code NAME}. The url has to use https. A host other than Discord's is allowed for relays, with a notice. Messages
 * about the url never contain it.
 */
public final class WebhookUrl {
    private static final String ENV_PREFIX = "env:";
    private static final Set<String> DISCORD_HOSTS =
            Set.of("discord.com", "discordapp.com", "ptb.discord.com", "canary.discord.com");

    private WebhookUrl() {}

    /**
     * Resolves the configured value.
     *
     * @param configured  the configured url or {@code env:NAME}
     * @param environment reads an environment variable, null when it is unset
     * @param notice      receives what is wrong or unusual about the url
     * @return the url, empty when none is set or it is not usable
     */
    public static Optional<URI> resolve(String configured, UnaryOperator<String> environment, Consumer<String> notice) {
        var value = configured.trim();
        if (value.regionMatches(true, 0, ENV_PREFIX, 0, ENV_PREFIX.length())) {
            var name = value.substring(ENV_PREFIX.length()).trim();
            var resolved = name.isEmpty() ? null : environment.apply(name);
            if (resolved == null || resolved.isBlank()) {
                notice.accept("The environment variable " + name + " for the Discord webhook is not set.");
                return Optional.empty();
            }
            value = resolved.trim();
        }
        if (value.isEmpty()) {
            notice.accept("No Discord webhook url is configured.");
            return Optional.empty();
        }
        return parse(value, notice);
    }

    private static Optional<URI> parse(String value, Consumer<String> notice) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            notice.accept("The Discord webhook url is not a valid url.");
            return Optional.empty();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            notice.accept("The Discord webhook url has to start with https://.");
            return Optional.empty();
        }
        if (!DISCORD_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            notice.accept("The Discord webhook url does not point at Discord. Messages are sent to " + uri.getHost()
                    + " instead.");
        }
        return Optional.of(uri);
    }
}
