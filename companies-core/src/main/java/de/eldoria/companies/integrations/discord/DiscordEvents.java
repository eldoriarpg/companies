/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import de.eldoria.companies.configuration.elements.DiscordSettings;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.menus.order.EventTexts;
import de.eldoria.companies.util.Times;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Posts the starts and ends of server events to the webhook: {@link WebhookEvent#EVENT_STARTED} and
 * {@link WebhookEvent#EVENT_ENDED}.
 * <p>
 * Only the server that moved the event posts, since only it wins the move, so every start and end is posted once in
 * the whole network. An end covers an event that ended at its end or early, one that ran out of money, and one an
 * admin cancelled after it started; an event cancelled before its start or one that never started is not posted.
 */
public final class DiscordEvents {
    private final Supplier<DiscordSettings> settings;
    private final DiscordEmbeds embeds;
    private final DoubleFunction<String> prices;
    private final UnaryOperator<String> texts;
    private final Consumer<Embed> queue;

    /**
     * @param settings the webhook settings
     * @param embeds   builds the embeds
     * @param prices   formats an amount of money
     * @param texts    localizes a message key
     * @param queue    receives the embeds to post
     */
    public DiscordEvents(
            Supplier<DiscordSettings> settings,
            DiscordEmbeds embeds,
            DoubleFunction<String> prices,
            UnaryOperator<String> texts,
            Consumer<Embed> queue) {
        this.settings = settings;
        this.embeds = embeds;
        this.prices = prices;
        this.texts = texts;
        this.queue = queue;
    }

    /**
     * Posts what happened to an event, when the webhook posts it.
     *
     * @param event  the event as it is now
     * @param change what happened, see {@link de.eldoria.companies.nodes.NodeMessage.ServerEventChanged}
     */
    public void changed(ServerEvent event, String change) {
        switch (change) {
            case "ACTIVE" -> {
                if (!settings.get().posts(WebhookEvent.EVENT_STARTED)) return;
                var budget = event.kind().funded() && event.remaining().isPresent()
                        ? Optional.of(prices.apply(event.remaining().getAsDouble()))
                        : Optional.<String>empty();
                queue.accept(embeds.eventStarted(
                        event.name(),
                        EventTexts.plainEffect(event, texts),
                        scope(event),
                        event.ends().atZone(Times.UTC.getZone()).toInstant(),
                        budget));
            }
            case "ENDED" -> ended("discord.eventEnded", event);
            case "DEPLETED" -> ended("discord.eventDepleted", event);
            case "CANCELLED" -> ended("discord.eventCancelled", event);
            default -> {}
        }
    }

    private void ended(String titleKey, ServerEvent event) {
        if (!settings.get().posts(WebhookEvent.EVENT_ENDED)) return;
        queue.accept(embeds.eventEnded(titleKey, event.name()));
    }

    private String scope(ServerEvent event) {
        List<String> parts = new ArrayList<>();
        if (!event.materials().isEmpty()) parts.add(EventTexts.items(event));
        if (!event.levels().isEmpty()) {
            parts.add(texts.apply("words.levels") + " "
                    + event.levels().stream().map(String::valueOf).collect(Collectors.joining(", ")));
        }
        return parts.isEmpty() ? texts.apply("discord.eventScopeAll") : String.join(" · ", parts);
    }
}
