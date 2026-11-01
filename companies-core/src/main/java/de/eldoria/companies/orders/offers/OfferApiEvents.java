/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.offer.ContestPlace;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.events.offer.ContestDeliveryEvent;
import de.eldoria.companies.events.offer.ContestJoinEvent;
import de.eldoria.companies.events.offer.ContestLeaveEvent;
import de.eldoria.companies.events.offer.ContestMembershipEvent;
import de.eldoria.companies.events.offer.ContestSettledEvent;
import de.eldoria.companies.events.offer.OfferEndEvent;
import de.eldoria.companies.events.offer.OfferStartEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fires the API events of event offers where their change is won: {@link OfferStartEvent} and
 * {@link ContestSettledEvent} as an {@link EventOfferService.Announcer} on the primary server, {@link OfferEndEvent}
 * where an offer that ran closed, the contest events where a company joins, leaves or delivers.
 */
public final class OfferApiEvents implements EventOfferService.Announcer {
    /**
     * Fires nothing, for tests and services without a server.
     */
    public static final OfferApiEvents NONE = new OfferApiEvents(null, id -> {
        throw new IllegalStateException("No players without a server");
    });

    private final @Nullable Consumer<Event> call;
    private final Function<UUID, OfflinePlayer> players;

    /**
     * @param call    fires an event on the calling thread, null to fire nothing
     * @param players finds a player by id
     */
    public OfferApiEvents(@Nullable Consumer<Event> call, Function<UUID, OfflinePlayer> players) {
        this.call = call;
        this.players = players;
    }

    private void fire(Supplier<Event> event) {
        if (call != null) call.accept(event.get());
    }

    @Override
    public void reached(EventOffer offer) {}

    @Override
    public void started(EventOffer offer) {
        fire(() -> new OfferStartEvent(new ApiOffer(offer)));
    }

    @Override
    public void contestClosed(EventOffer offer, List<ContestEntry> ranking, List<ContestPrize> prizes) {
        if (call == null) return;
        List<ContestPlace> places = new ArrayList<>();
        var last = 0;
        for (var index = 0; index < ranking.size(); index++) {
            var entry = ranking.get(index);
            var won = prizes.stream()
                    .filter(found -> Integer.valueOf(entry.company()).equals(found.company()))
                    .findFirst();
            var place = won.map(ContestPrize::place).orElse(Math.max(index + 1, last + 1));
            last = place;
            var prize = won.filter(ContestPrize::paid).map(ContestPrize::amount).orElse(0.0);
            places.add(new ContestPlace(
                    place, entry.company(), entry.name() == null ? "" : entry.name(), entry.delivered(), prize));
        }
        fire(() -> new ContestSettledEvent(new ApiOffer(offer), places));
    }

    /**
     * Fires {@link OfferEndEvent} for an offer that just closed, when it ever ran.
     *
     * @param offer the offer, closed
     */
    @Override
    public void ended(@Nullable EventOffer offer) {
        if (offer == null || offer.started() == null || !offer.state().closed()) return;
        fire(() -> new OfferEndEvent(new ApiOffer(offer), reason(offer.state())));
    }

    /**
     * Asks the listeners whether a company may join or leave a contest.
     *
     * @param join    true to join, false to leave
     * @param offer   the contest
     * @param company the company
     * @param member  the member acting for it
     * @return why the change was refused, empty when it may go on
     */
    public Optional<CommandFailure> allows(boolean join, EventOffer offer, ICompanyProfile company, UUID member) {
        if (call == null) return Optional.empty();
        var player = players.apply(member);
        ContestMembershipEvent event = join
                ? new ContestJoinEvent(new ApiOffer(offer), company, player)
                : new ContestLeaveEvent(new ApiOffer(offer), company, player);
        call.accept(event);
        if (!event.isCancelled()) return Optional.empty();
        return Optional.of(event.cancelReason()
                .map(reason -> CommandFailure.of("error.cancelledByIntegration", reason))
                .orElseGet(() -> CommandFailure.actionBar("error.cancelledByIntegration")));
    }

    /**
     * Fires {@link ContestDeliveryEvent} for a delivery into a contest and, when it closed the contest,
     * {@link OfferEndEvent}.
     *
     * @param data    offer repository, read for the contest and its ranking after the delivery
     * @param id      id of the contest
     * @param company the worker's company
     * @param worker  the worker
     * @param items   items taken
     * @param paid    what they were paid before tax and share
     * @param closed  whether this delivery closed the contest, filling it or spending its money
     */
    public void delivered(
            AOfferData data, int id, ICompanyProfile company, UUID worker, int items, double paid, boolean closed) {
        if (call == null) return;
        if (!closed && ContestDeliveryEvent.getHandlerList().getRegisteredListeners().length == 0) return;
        var found = data.offer(id);
        if (found.isEmpty()) return;
        var offer = found.get();
        var ranking = data.ranking(id).stream()
                .filter(entry -> entry.delivered() > 0 && entry.name() != null)
                .toList();
        var place = 0;
        var delivered = 0;
        for (var index = 0; index < ranking.size(); index++) {
            if (ranking.get(index).company() == company.id()) {
                place = index + 1;
                delivered = ranking.get(index).delivered();
            }
        }
        call.accept(new ContestDeliveryEvent(
                new ApiOffer(offer), company, players.apply(worker), items, paid, place, delivered));
        if (closed) ended(offer);
    }

    private static OfferEndEvent.Reason reason(OfferState state) {
        return switch (state) {
            case REACHED -> OfferEndEvent.Reason.REACHED;
            case DEPLETED -> OfferEndEvent.Reason.BUDGET_SPENT;
            case CANCELLED -> OfferEndEvent.Reason.CANCELLED;
            default -> OfferEndEvent.Reason.ENDED;
        };
    }
}
