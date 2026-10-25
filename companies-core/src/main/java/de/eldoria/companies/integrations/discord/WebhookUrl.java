/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Resolves the configured webhook url.
 * <p>
 * The url comes from the configuration, or from the environment variable {@code COMPANIES_DISCORD_WEBHOOK}, see
 * {@link de.eldoria.companies.configuration.elements.DiscordSettings#url()}. The url has to use https. A host other than Discord's is allowed for relays, with a notice. Messages
 * about the url never contain it.
 */
public final class WebhookUrl {
    private static final Set<String> DISCORD_HOSTS =
            Set.of("discord.com", "discordapp.com", "ptb.discord.com", "canary.discord.com");

    private WebhookUrl() {}

    /**
     * Resolves the configured value.
     *
     * @param configured the configured url
     * @param notice     receives what is wrong or unusual about the url
     * @return the url, empty when none is set or it is not usable
     */
    public static Optional<URI> resolve(String configured, Consumer<String> notice) {
        var value = configured == null ? "" : configured.trim();
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
