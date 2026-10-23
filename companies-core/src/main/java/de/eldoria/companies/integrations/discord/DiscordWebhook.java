/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import de.eldoria.companies.configuration.elements.DiscordSettings;
import de.eldoria.eldoutilities.localization.ILocalizer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.InstantSource;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The running Discord webhook: its client and the listener feeding it.
 */
public final class DiscordWebhook {
    private static final Duration FLUSH = Duration.ofSeconds(3);

    private final WebhookClient client;
    private final DiscordListener listener;

    private DiscordWebhook(WebhookClient client, DiscordListener listener) {
        this.client = client;
        this.listener = listener;
    }

    /**
     * Starts the webhook when it is enabled and its url resolves. What keeps it from starting is logged without the
     * url. The url, the limits and whether it is enabled are read once; the list of events follows a reloaded
     * configuration.
     *
     * @param plugin    the plugin, for its logger
     * @param current   reads the current webhook settings
     * @param economy   formats prices
     * @param localizer localizes the embed texts
     * @return the running webhook, empty when disabled or not configured
     */
    public static Optional<DiscordWebhook> start(
            Plugin plugin, Supplier<DiscordSettings> current, Economy economy, ILocalizer localizer) {
        var settings = current.get();
        if (!settings.enabled()) return Optional.empty();
        var logger = plugin.getLogger();
        var resolved = WebhookUrl.resolve(settings.url(), System::getenv, logger::warning);
        if (resolved.isEmpty()) return Optional.empty();
        var uri = resolved.get();
        var client = new WebhookClient(
                new HttpWebhookSender(uri),
                new WebhookPayload(settings.username(), settings.avatarUrl()),
                new WebhookRedaction(uri.toString()),
                new WebhookClient.Limits(settings.queueSize(), settings.maxPerMinute()),
                InstantSource.system(),
                Sleeper.THREAD,
                logger);
        var embeds = new DiscordEmbeds(localizer::localize, InstantSource.system());
        var listener = new DiscordListener(current, embeds, economy::format, client::enqueue);
        client.start();
        logger.info("Posting to the Discord webhook.");
        return Optional.of(new DiscordWebhook(client, listener));
    }

    /**
     * The listener to register for the plugin's events.
     *
     * @return the listener
     */
    public Listener listener() {
        return listener;
    }

    /**
     * Stops the webhook, sending what is queued for up to three seconds.
     */
    public void stop() {
        client.stop(FLUSH);
    }
}
