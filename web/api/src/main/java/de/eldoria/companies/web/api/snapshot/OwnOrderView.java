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
 * An order the player placed. The company working on it is never part of it.
 *
 * @param id the order id
 * @param name the order's name
 * @param state its state
 * @param lines its lines with progress
 * @param created when it was placed
 * @param deadline when a claim runs out, if claimed
 * @param waiting items delivered and waiting to be received
 */
public record OwnOrderView(
        int id,
        String name,
        OrderState state,
        List<OrderLineView> lines,
        Instant created,
        @Nullable Instant deadline,
        List<ItemView> waiting) {}
