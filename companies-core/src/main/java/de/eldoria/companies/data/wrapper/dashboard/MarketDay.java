/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.dashboard;

import java.time.LocalDate;

/**
 * What the orders of a material completed on one day add up to. Exact lines count under their material.
 *
 * @param day      the day the orders were completed
 * @param material the material name, e.g. {@code SPRUCE_PLANKS}
 * @param orders   the number of completed orders with a line of the material
 * @param items    the number of items of the material in them
 * @param avg      the average unit price of the lines
 * @param min      the lowest unit price
 * @param max      the highest unit price
 */
public record MarketDay(LocalDate day, String material, int orders, long items, double avg, double min, double max) {}
