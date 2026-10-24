/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

/**
 * A company's place in a ranking.
 *
 * @param place the place, starting at 1
 * @param company the company id
 * @param name the company name
 * @param value the value the ranking orders by
 */
public record RankingEntry(int place, int company, String name, double value) {}
