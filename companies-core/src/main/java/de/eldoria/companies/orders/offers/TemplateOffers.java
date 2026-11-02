/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.commands.companyadmin.OffersCommand;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.events.EventSettings;
import de.eldoria.companies.configuration.elements.events.OfferTemplate;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.services.events.EventSchedule;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Creates the goals and contests of a template of {@code events.yml} when the template created an event: each is
 * checked as {@code /companyadmin offers goal} and {@code contest} check it, put on the event with its times, so it
 * starts, ends and is cancelled with the event, and announced like any new offer. An offer without a name takes the
 * event's name, cut to {@value EventOffer#MAX_NAME_LENGTH} characters. An offer the event already has from its
 * template, the same name asking for the same items, is not created again. An offer that is refused is logged with the reason and left out; the event and the other
 * offers are created all the same. The offers are created by {@value EventSchedule#CREATOR}.
 */
public final class TemplateOffers implements Consumer<ServerEvent> {
    private final Supplier<EventSettings> settings;
    private final AOfferData offers;
    private final Runnable checkSoon;
    private final Clock clock;
    private final Logger logger;

    /**
     * @param settings  the current settings of the events
     * @param offers    offer repository
     * @param checkSoon asks the primary to look after the offers soon
     * @param clock     clock giving the current time in UTC
     * @param logger    reports refused offers
     */
    public TemplateOffers(
            Supplier<EventSettings> settings, AOfferData offers, Runnable checkSoon, Clock clock, Logger logger) {
        this.settings = settings;
        this.offers = offers;
        this.checkSoon = checkSoon;
        this.clock = clock;
        this.logger = logger;
    }

    /**
     * Creates the offers of the template that created an event that it does not have yet; an event of no template
     * gets none.
     *
     * @param event the event, stored
     * @throws IllegalStateException when an offer could not be stored, so the event is handed over again later
     */
    @Override
    public void accept(ServerEvent event) {
        var template = event.template();
        if (template == null) return;
        var found = settings.get().templates().stream()
                .filter(candidate -> candidate.id().equals(template))
                .findFirst();
        if (found.isEmpty() || found.get().offers().isEmpty()) return;
        var existing = offers.ofEvent(event.id()).stream()
                .filter(offer -> EventSchedule.CREATOR.equals(offer.createdBy()))
                .collect(Collectors.toCollection(ArrayList::new));
        var created = false;
        var failed = false;
        var definitions = found.get().offers();
        for (var index = 0; index < definitions.size(); index++) {
            var offer = draft(event, definitions.get(index), index);
            if (offer == null || matched(existing, offer)) continue;
            try {
                if (offers.create(offer).isPresent()) {
                    created = true;
                } else {
                    failed = true;
                }
            } catch (RuntimeException e) {
                failed = true;
                logger.log(Level.SEVERE, "Could not create offer " + (index + 1) + " of template " + template, e);
            }
        }
        if (created) checkSoon.run();
        if (failed) throw new IllegalStateException("An offer of template " + template + " could not be stored");
    }

    private static boolean matched(List<EventOffer> existing, EventOffer offer) {
        for (var iterator = existing.iterator(); iterator.hasNext(); ) {
            var other = iterator.next();
            if (!other.name().equals(offer.name()) || !other.line().equals(offer.line())) continue;
            iterator.remove();
            return true;
        }
        return false;
    }

    /**
     * The offer of a definition on an event, checked; null when it is refused. Start and run time only pass the
     * checks, the offer takes the event's times, so they are kept within {@link GoalDraft#MAX_HOURS}.
     */
    private @Nullable EventOffer draft(ServerEvent event, OfferTemplate definition, int index) {
        var now = LocalDateTime.now(clock);
        var name = definition.name().isEmpty() ? fallbackName(event) : definition.name();
        var startHours = hours(Duration.between(now, event.starts()), 0);
        var hours = hours(Duration.between(event.starts(), event.ends()), 1);
        var budget = definition.budget() > 0 ? definition.budget() : null;
        try {
            var line = line(definition);
            var offer = definition.kind() == OfferKind.CONTEST
                    ? GoalDraft.contest(
                            name,
                            definition.money(),
                            line,
                            definition.amount(),
                            definition.price(),
                            GoalDraft.prizes(definition.prizes().isEmpty() ? GoalDraft.NO_PRIZES : definition.prizes()),
                            startHours,
                            hours,
                            budget,
                            EventSchedule.CREATOR,
                            now)
                    : GoalDraft.goal(
                            name,
                            definition.money(),
                            line,
                            definition.amount(),
                            definition.price(),
                            definition.chunk(),
                            definition.open(),
                            startHours,
                            hours,
                            budget,
                            EventSchedule.CREATOR,
                            now);
            return offer.onEvent(event.id(), event.starts(), event.ends());
        } catch (CommandFailure failure) {
            logger.warning("Offer " + (index + 1) + " of the template " + event.template() + " of events.yml ("
                    + describe(definition, name) + ") is not created for event " + event.id() + ": "
                    + failure.localeKey() + ". Fix it and reload.");
            return null;
        }
    }

    private static String fallbackName(ServerEvent event) {
        var name = event.name();
        return name.substring(0, Math.min(name.length(), EventOffer.MAX_NAME_LENGTH))
                .strip();
    }

    private static int hours(Duration duration, int least) {
        var hours = Math.ceil(duration.toMinutes() / 60.0);
        return (int) Math.min(GoalDraft.MAX_HOURS, Math.max(least, hours));
    }

    private static String describe(OfferTemplate definition, String name) {
        var asks = definition.criteria().isEmpty() ? definition.material() : definition.criteria();
        return definition.kind().name().toLowerCase(Locale.ROOT) + " '" + name + "' of " + definition.amount() + " "
                + asks + " at " + definition.price() + ", money " + definition.money();
    }

    private static OfferLine line(OfferTemplate definition) {
        if (!definition.criteria().isEmpty()) return GoalDraft.criteria(definition.criteria());
        return GoalDraft.material(definition.material(), OffersCommand::canonical);
    }
}
