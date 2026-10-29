/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import de.eldoria.companies.configuration.elements.DiscordSettings;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.orders.offers.EventOfferService;
import de.eldoria.companies.util.Times;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Posts what happens to event offers to the webhook: {@link WebhookEvent#OFFER_STARTED},
 * {@link WebhookEvent#GOAL_PROGRESS} and {@link WebhookEvent#CONTEST_RESULT}.
 * <p>
 * The primary announces each of them once, so it alone posts them. The thresholds of a goal's progress are stored with
 * the goal, so a restart posts none twice.
 */
public final class DiscordOffers implements EventOfferService.Announcer {
    private static final int PLACES = 10;

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
    public DiscordOffers(
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

    @Override
    public void reached(EventOffer offer) {}

    @Override
    public void started(EventOffer offer) {
        if (!settings.get().posts(WebhookEvent.OFFER_STARTED)) return;
        var kind = texts.apply(offer.kind() == OfferKind.CONTEST ? "offers.kind.contest" : "offers.kind.goal");
        queue.accept(embeds.offerStarted(
                offer.name(),
                kind,
                offer.amount(),
                offer.line().label(),
                prices.apply(offer.unitPrice()),
                offer.ends().atZone(Times.UTC.getZone()).toInstant()));
    }

    @Override
    public void goalProgress(EventOffer offer, int threshold) {
        if (!settings.get().posts(WebhookEvent.GOAL_PROGRESS)) return;
        queue.accept(embeds.goalProgress(offer.name(), threshold, offer.delivered(), offer.amount()));
    }

    @Override
    public void contestClosed(EventOffer offer, List<ContestEntry> ranking, List<ContestPrize> prizes) {
        if (!settings.get().posts(WebhookEvent.CONTEST_RESULT)) return;
        var places = ranking.stream().limit(PLACES).map(entry -> {
            var prize = prizes.stream()
                    .filter(found -> found.paid() && Objects.equals(found.company(), entry.company()))
                    .findFirst()
                    .map(found -> prices.apply(found.amount()));
            return new DiscordEmbeds.Place(
                    ranking.indexOf(entry) + 1,
                    entry.name() == null ? "#" + entry.company() : entry.name(),
                    entry.delivered(),
                    prize);
        });
        queue.accept(embeds.contestResult(offer.name(), places.toList()));
    }

    @Override
    public void contestCancelled(EventOffer offer) {
        if (!settings.get().posts(WebhookEvent.CONTEST_RESULT)) return;
        queue.accept(embeds.contestCancelled(offer.name()));
    }
}
