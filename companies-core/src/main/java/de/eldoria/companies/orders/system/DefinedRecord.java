/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.configuration.elements.systemorders.RulePrice;
import org.jetbrains.annotations.Nullable;

/**
 * The numbers a defined rule placed an order on, stored as the order's decision so an admin can see why it exists.
 *
 * @param rule       id of the rule
 * @param name       name of the rule
 * @param material   material ordered
 * @param amount     items ordered
 * @param mode       how the price was found
 * @param average    market average per item, null for a fixed price
 * @param factor     factor on the average
 * @param base       fixed price or average times factor
 * @param jitter     share drawn within the jitter
 * @param min        lowest price per item of the rule
 * @param max        highest price per item of the rule, 0 for none
 * @param bounded    whether a bound changed the price
 * @param unit       price per item paid
 * @param total      price of the order
 * @param source     where the money came from
 * @param account    economy account that paid, null for minted money
 * @param budgetLeft what the rule may still spend today, null without a budget of its own
 * @param mintedLeft minted money all rules may still spend today, null for an account
 */
public record DefinedRecord(
        String rule,
        String name,
        String material,
        int amount,
        RulePrice.PriceMode mode,
        @Nullable Double average,
        double factor,
        double base,
        double jitter,
        double min,
        double max,
        boolean bounded,
        double unit,
        double total,
        MoneySource.SourceType source,
        @Nullable String account,
        @Nullable Double budgetLeft,
        @Nullable Double mintedLeft) {
    /**
     * The kind stored with a decision of a defined rule.
     */
    public static final String KIND = "DEFINED";
}
