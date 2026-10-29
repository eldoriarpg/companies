/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import org.jetbrains.annotations.Nullable;

/**
 * The numbers a chunk of an event offer was placed on, stored as the decision of its system order, so
 * {@code /companyadmin systemorders info} explains it like the orders of rules.
 *
 * @param offer      id of the offer
 * @param name       name of the offer
 * @param item       what the chunk orders: the material, the criteria or the exact item's name
 * @param amount     items of the chunk
 * @param unit       price per item
 * @param total      price of the chunk
 * @param source     where the money came from
 * @param account    economy account that paid, null for minted money
 * @param budgetLeft what the offer could still spend after this chunk
 */
public record OfferRecord(
        int offer,
        String name,
        String item,
        int amount,
        double unit,
        double total,
        MoneySource.SourceType source,
        @Nullable String account,
        @Nullable Double budgetLeft) {
    /**
     * The kind stored with the decision of a chunk.
     */
    public static final String KIND = "OFFER";
}
