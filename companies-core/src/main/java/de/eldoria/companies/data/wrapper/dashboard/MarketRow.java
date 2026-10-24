/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.dashboard;

/**
 * What the delivered orders of a material add up to. Exact lines are left out, like in the market prices.
 *
 * @param material the material name, e.g. {@code SPRUCE_PLANKS}
 * @param orders   the number of delivered orders with a line of the material
 * @param volume   the number of items ordered in them
 * @param avg      the average unit price
 * @param min      the lowest unit price
 * @param max      the highest unit price
 */
public record MarketRow(String material, int orders, long volume, double avg, double min, double max) {}
