/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A page of a ranking.
 *
 * @param ranking the ranking's key, e.g. {@code orders}; an open set
 * @param range the time range's key, e.g. {@code all}; an open set
 * @param page the page, starting at 1
 * @param pages the number of pages
 * @param entries the entries of the page
 * @param own the place of the player's company, none without one
 */
public record RankingPage(
        String ranking,
        String range,
        int page,
        int pages,
        List<RankingEntry> entries,
        @Nullable RankingEntry own) {}
