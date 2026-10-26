/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

/**
 * How much of one material players' orders got delivered in a span of time, and how much of it lately.
 *
 * @param material    the material, as order lines store it
 * @param orders      delivered orders with the material
 * @param items       items of the material in them
 * @param recentItems items of the material in the orders delivered lately
 */
public record DeliveredVolume(String material, int orders, long items, long recentItems) {}
