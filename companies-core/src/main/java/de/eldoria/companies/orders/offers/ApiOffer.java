/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.components.offer.EventOfferKind;
import de.eldoria.companies.components.offer.EventOfferState;
import de.eldoria.companies.components.offer.IEventOffer;
import de.eldoria.companies.data.wrapper.offer.EventOffer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * An event offer as the API shows it.
 *
 * @param offer the stored offer
 */
public record ApiOffer(EventOffer offer) implements IEventOffer {
    @Override
    public int id() {
        return offer.id();
    }

    @Override
    public EventOfferKind kind() {
        return EventOfferKind.valueOf(offer.kind().name());
    }

    @Override
    public String name() {
        return offer.name();
    }

    @Override
    public EventOfferState state() {
        return EventOfferState.valueOf(offer.state().name());
    }

    @Override
    public OptionalInt event() {
        var event = offer.event();
        return event == null ? OptionalInt.empty() : OptionalInt.of(event);
    }

    @Override
    public String item() {
        return offer.line().label();
    }

    @Override
    public int amount() {
        return offer.amount();
    }

    @Override
    public int delivered() {
        return offer.delivered();
    }

    @Override
    public double unitPrice() {
        return offer.unitPrice();
    }

    @Override
    public double budget() {
        return offer.budget();
    }

    @Override
    public double spent() {
        return Math.max(0, offer.spent());
    }

    @Override
    public List<Double> prizes() {
        return offer.prizes();
    }

    @Override
    public LocalDateTime starts() {
        return offer.starts();
    }

    @Override
    public LocalDateTime ends() {
        return offer.ends();
    }

    @Override
    public Optional<LocalDateTime> closedAt() {
        return Optional.ofNullable(offer.closed());
    }
}
