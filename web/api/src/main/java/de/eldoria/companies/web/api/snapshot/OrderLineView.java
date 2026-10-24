/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

/**
 * One line of an order.
 *
 * @param item the ordered item and amount
 * @param delivered how many were delivered
 * @param price the price of the whole line
 */
public record OrderLineView(ItemView item, int delivered, double price) {}
