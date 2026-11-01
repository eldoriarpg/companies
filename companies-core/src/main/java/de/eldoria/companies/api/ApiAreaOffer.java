/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.components.area.IAreaOffer;
import de.eldoria.companies.components.area.OfferKind;
import de.eldoria.companies.data.wrapper.area.AreaOffer;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * A stored area offer as the API shows it.
 *
 * @param offer the stored offer
 */
record ApiAreaOffer(AreaOffer offer) implements IAreaOffer {
    @Override
    public int id() {
        return offer.id();
    }

    @Override
    public String provider() {
        return offer.provider();
    }

    @Override
    public String ref() {
        return offer.ref();
    }

    @Override
    public String world() {
        return offer.world();
    }

    @Override
    public UUID node() {
        return offer.node();
    }

    @Override
    public OfferKind kind() {
        return offer.kind();
    }

    @Override
    public double price() {
        return offer.price();
    }

    @Override
    public int periodDays() {
        return offer.periodDays();
    }

    @Override
    public Optional<UUID> keeper() {
        return Optional.ofNullable(offer.keeper());
    }

    @Override
    public LocalDateTime created() {
        return offer.created();
    }
}
