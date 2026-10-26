/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import org.jetbrains.annotations.Nullable;

/**
 * The steps that led the server to guess an order, stored as the order's decision so an admin can see why it exists.
 *
 * @param material    material ordered
 * @param amount      items ordered
 * @param typical     items per delivered player order, 0 when unknown
 * @param sizeShare   share of the typical amount asked for
 * @param orders      delivered player orders in the history
 * @param longRate    items delivered per day over the history
 * @param recentRate  items delivered per day lately
 * @param score       demand score, recent rate against long rate
 * @param rank        rank of the material among the candidates, from 1
 * @param candidates  how many candidates there were
 * @param competition how the order keeps out of the way of players
 * @param playerItems items of the material in open player orders, 0 without any
 * @param lowest      lowest open player price per item, null without open player orders
 * @param cap         highest price an underbidding order may pay, null when not underbidding
 * @param average     market average per item
 * @param factor      factor on the average
 * @param step        reprice step, 0 for a new order
 * @param replaces    the order this one replaced at a higher price, null for a new one
 * @param jitter      share drawn within the jitter
 * @param bounded     whether the minimum or maximum price changed the price
 * @param unit        price per item
 * @param total       price of the order
 * @param openPlayer  open player orders when it was placed
 * @param openGuessed open guessed orders before it was placed
 * @param target      guessed orders the share, minimum and maximum allowed
 * @param source      where the money came from
 * @param account     economy account that paid, null for minted money
 * @param budgetLeft  what guessed orders may still spend that day, null without a budget
 * @param mintedLeft  minted money all rules may still spend that day, null for an account
 */
public record GuessedRecord(
        String material,
        int amount,
        int typical,
        double sizeShare,
        int orders,
        double longRate,
        double recentRate,
        double score,
        int rank,
        int candidates,
        GuessedSettings.Competition competition,
        long playerItems,
        @Nullable Double lowest,
        @Nullable Double cap,
        double average,
        double factor,
        int step,
        @Nullable Integer replaces,
        double jitter,
        boolean bounded,
        double unit,
        double total,
        int openPlayer,
        int openGuessed,
        int target,
        MoneySource.SourceType source,
        @Nullable String account,
        @Nullable Double budgetLeft,
        @Nullable Double mintedLeft) {
    /**
     * The kind stored with a decision of a guessed order.
     */
    public static final String KIND = "GUESSED";
}
