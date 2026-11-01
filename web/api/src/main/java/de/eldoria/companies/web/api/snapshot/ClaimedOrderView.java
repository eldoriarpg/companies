/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * An order the player's company works on. The owner is never part of it.
 *
 * @param id the order id
 * @param name the order's name
 * @param state its state
 * @param lines its lines with progress
 * @param claimed when it was claimed
 * @param deadline when the claim runs out
 * @param offer the event offer the order is a chunk of, null for any other order
 */
public record ClaimedOrderView(
        int id,
        String name,
        OrderState state,
        List<OrderLineView> lines,
        Instant claimed,
        @Nullable Instant deadline,
        @Nullable OfferBadgeView offer) {}
