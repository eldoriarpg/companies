/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A perk of the server as a company sees it.
 *
 * @param id          the id of the perk
 * @param name        its name, plain text
 * @param description what it gives, plain text
 * @param price       what it costs, paid once from the treasury
 * @param minLevel    the company level from which it can be bought and used
 * @param state       whether the company owns it, has it paused, may buy it or needs a higher level
 * @param bought      when the company got it, none unless it owns it
 */
public record PerkView(
        String id,
        String name,
        String description,
        double price,
        int minLevel,
        PerkStateView state,
        @Nullable Instant bought) {}
