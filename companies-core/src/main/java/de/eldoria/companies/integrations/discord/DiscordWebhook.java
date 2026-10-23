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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The Discord webhook: the listener feeding it and the client posting to it.
 * <p>
 * The listener lives as long as the plugin. The client is replaced on {@link #restart()}, which reads the url, the
 * limits, the username, the avatar and whether the webhook is enabled again. A webhook disabled because Discord no
 * longer knew it is tried again the same way. The list of events always follows the current configuration.
 */
public final class DiscordWebhook {
    private static final Duration FLUSH = Duration.ofSeconds(3);

    private final Supplier<DiscordSettings> current;
    private final Function<DiscordSettings, Optional<WebhookClient>> connect;
    private final Logger logger;
    private final DiscordListener listener;
    private final AtomicReference<WebhookClient> client = new AtomicReference<>();

    /**
     * @param current reads the current webhook settings
     * @param connect creates a client for the settings, empty when they do not lead to one
     * @param embeds  builds the embeds
     * @param prices  formats an amount of money
     * @param logger  logger of the plugin
     */
    DiscordWebhook(
            Supplier<DiscordSettings> current,
            Function<DiscordSettings, Optional<WebhookClient>> connect,
            DiscordEmbeds embeds,
            DoubleFunction<String> prices,
            Logger logger) {
        this.current = current;
        this.connect = connect;
        this.logger = logger;
        listener = new DiscordListener(current, embeds, prices, this::enqueue);
    }

    /**
     * Creates the webhook and starts its client when it is enabled and its url resolves. What keeps it from starting
     * is logged without the url.
     *
     * @param plugin    the plugin, for its logger
     * @param current   reads the current webhook settings
     * @param economy   formats prices
     * @param localizer localizes the embed texts
     * @return the webhook, posting only while it is enabled and configured
     */
    public static DiscordWebhook start(
            Plugin plugin, Supplier<DiscordSettings> current, Economy economy, ILocalizer localizer) {
        var logger = plugin.getLogger();
        var webhook = new DiscordWebhook(
                current,
                settings -> connect(settings, logger),
                new DiscordEmbeds(localizer::localize, InstantSource.system()),
                economy::format,
                logger);
        webhook.restart();
        return webhook;
    }

    private static Optional<WebhookClient> connect(DiscordSettings settings, Logger logger) {
        if (!settings.enabled()) return Optional.empty();
        var resolved = WebhookUrl.resolve(settings.url(), System::getenv, logger::warning);
        if (resolved.isEmpty()) return Optional.empty();
        var uri = resolved.get();
        return Optional.of(new WebhookClient(
                new HttpWebhookSender(uri),
                new WebhookPayload(settings.username(), settings.avatarUrl()),
                new WebhookRedaction(uri.toString()),
                new WebhookClient.Limits(settings.queueSize(), settings.maxPerMinute()),
                InstantSource.system(),
                Sleeper.THREAD,
                logger));
    }

    /**
     * Replaces the client with one built from the current settings. The previous client sends what it has queued for
     * up to three seconds on a thread of its own.
     */
    public void restart() {
        var next = connect.apply(current.get());
        next.ifPresent(WebhookClient::start);
        var previous = client.getAndSet(next.orElse(null));
        if (previous != null) Thread.ofVirtual().start(() -> previous.stop(FLUSH));
        if (next.isPresent()) logger.info("Posting to the Discord webhook.");
    }

    /**
     * Whether a client is posting.
     *
     * @return true when the webhook is enabled, configured and not disabled after Discord refused it
     */
    public boolean running() {
        var running = client.get();
        return running != null && !running.disabled();
    }

    private void enqueue(Embed embed) {
        var running = client.get();
        if (running != null) running.enqueue(embed);
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
        var running = client.getAndSet(null);
        if (running != null) running.stop(FLUSH);
    }
}
