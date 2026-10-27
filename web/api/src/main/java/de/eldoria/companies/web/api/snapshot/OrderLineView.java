/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import org.jspecify.annotations.Nullable;

/**
 * One line of an order.
 *
 * @param item the ordered item and amount; for a criteria line the first item of its set, named after the set
 * @param delivered how many were delivered
 * @param price the price of the whole line
 * @param criteria what a criteria line accepts, null for other lines
 */
public record OrderLineView(
        ItemView item,
        int delivered,
        double price,
        @Nullable CriteriaView criteria) {}
