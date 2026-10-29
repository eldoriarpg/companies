/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.orders.OrderBuilder;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * An offer the server makes for an event or a shared goal, stored in {@code event_offers}.
 * <p>
 * A goal asks for {@code amount} items at {@code unitPrice} each and places them as system orders of
 * {@code chunkSize} items, keeping {@code openChunks} of them waiting to be taken. Its money comes from an economy
 * account or is minted, within {@code budget} for the whole offer; {@code spent} is what its open and delivered chunks
 * hold, {@code delivered} the items delivered so far. Both are kept by the chunks' changes and survive the purge of
 * their orders.
 * <p>
 * A contest uses the same row without chunks: companies join it and deliver into it, each delivery paid at once per
 * item at {@code unitPrice}; {@code spent} is what its deliveries and, once it closed, its prizes were paid. Its
 * {@code prizes} are kept back from the budget up front. An account pays the whole budget when the contest starts
 * ({@code held}) and gets back what was not spent when it is settled after its close.
 *
 * @param id         id of the offer, 0 before it is stored
 * @param kind       goal or contest
 * @param name       name shown to players, also the name of its orders
 * @param event      id of the server event it belongs to; reserved for server events, always null for now
 * @param line       what it orders
 * @param amount     items asked for in all
 * @param unitPrice  price paid per item
 * @param chunkSize  items per chunk of a goal
 * @param openChunks chunks of a goal kept waiting to be taken at once
 * @param source     where the money comes from
 * @param account    the economy account of {@link MoneySource.SourceType#ACCOUNT}, null for minted money
 * @param budget     what the whole offer may spend
 * @param spent      what its open and delivered chunks hold; for a contest what was paid out
 * @param delivered  items delivered so far
 * @param starts     when it starts
 * @param ends       when it ends; chunks taken before may still be delivered after
 * @param state      where it is in its life
 * @param createdBy  who created it
 * @param created    when it was created
 * @param closed     when it was closed, null while it runs
 * @param prizes     the money of the first places of a contest, first place first; empty for a goal
 * @param held       what an account paid into a contest when it started, its whole budget; 0 for minted money
 * @param started    when a contest started and its money was taken, null before
 * @param settled    when a closed contest was settled: its places fixed and its prizes reserved; null before
 * @param refund     what went back to the account of a settled contest, null before
 * @param refunded   when a settled contest gave back its rest and announced its result, null before
 */
public record EventOffer(
        int id,
        OfferKind kind,
        String name,
        @Nullable Integer event,
        OfferLine line,
        int amount,
        double unitPrice,
        int chunkSize,
        int openChunks,
        MoneySource.SourceType source,
        @Nullable String account,
        double budget,
        double spent,
        int delivered,
        LocalDateTime starts,
        LocalDateTime ends,
        OfferState state,
        String createdBy,
        LocalDateTime created,
        @Nullable LocalDateTime closed,
        List<Double> prizes,
        double held,
        @Nullable LocalDateTime started,
        @Nullable LocalDateTime settled,
        @Nullable Double refund,
        @Nullable LocalDateTime refunded) {
    /**
     * @param prizes the money of the first places
     */
    public EventOffer {
        prizes = List.copyOf(prizes);
    }

    /**
     * The rule id the chunks of every offer are placed under as system orders. Defined rules may not use it, and the
     * caps and budgets of system order rules do not count the chunks.
     */
    public static final String RULE = "event-offer";

    /**
     * The longest name of an offer, which is also the name of its orders.
     */
    public static final int MAX_NAME_LENGTH = 31;

    /**
     * A new goal, not stored yet.
     *
     * @param name       name shown to players
     * @param line       what it orders
     * @param amount     items asked for in all
     * @param unitPrice  price paid per item
     * @param chunkSize  items per chunk
     * @param openChunks chunks kept waiting to be taken at once
     * @param source     where the money comes from
     * @param account    the economy account, null for minted money
     * @param budget     what the whole goal may spend
     * @param starts     when it starts
     * @param ends       when it ends
     * @param createdBy  who created it
     * @param created    when it was created
     * @return the goal
     */
    public static EventOffer goal(
            String name,
            OfferLine line,
            int amount,
            double unitPrice,
            int chunkSize,
            int openChunks,
            MoneySource.SourceType source,
            @Nullable String account,
            double budget,
            LocalDateTime starts,
            LocalDateTime ends,
            String createdBy,
            LocalDateTime created) {
        return new EventOffer(
                0,
                OfferKind.GOAL,
                name,
                null,
                line,
                amount,
                unitPrice,
                chunkSize,
                openChunks,
                source,
                account,
                budget,
                0,
                0,
                starts,
                ends,
                OfferState.SCHEDULED,
                createdBy,
                created,
                null,
                List.of(),
                0,
                null,
                null,
                null,
                null);
    }

    /**
     * A new contest, not stored yet. Its budget holds its prizes, which are kept back from the deliveries, so they can
     * always be paid.
     *
     * @param name      name shown to players
     * @param line      what it orders
     * @param amount    items asked for in all
     * @param unitPrice price paid per delivered item
     * @param prizes    the money of the first places, first place first
     * @param source    where the money comes from
     * @param account   the economy account, null for minted money
     * @param budget    what the whole contest may spend, deliveries and prizes
     * @param starts    when it starts
     * @param ends      when it ends
     * @param createdBy who created it
     * @param created   when it was created
     * @return the contest
     */
    public static EventOffer contest(
            String name,
            OfferLine line,
            int amount,
            double unitPrice,
            List<Double> prizes,
            MoneySource.SourceType source,
            @Nullable String account,
            double budget,
            LocalDateTime starts,
            LocalDateTime ends,
            String createdBy,
            LocalDateTime created) {
        return new EventOffer(
                0,
                OfferKind.CONTEST,
                name,
                null,
                line,
                amount,
                unitPrice,
                0,
                0,
                source,
                account,
                budget,
                0,
                0,
                starts,
                ends,
                OfferState.SCHEDULED,
                createdBy,
                created,
                null,
                prizes,
                0,
                null,
                null,
                null,
                null);
    }

    /**
     * @return the money of all prizes
     */
    public double prizeTotal() {
        var total = BigDecimal.ZERO;
        for (var prize : prizes) total = total.add(BigDecimal.valueOf(prize));
        return total.doubleValue();
    }

    /**
     * @return what deliveries into a contest may still be paid: the budget less the prizes and what was paid
     */
    public double deliveryMoneyLeft() {
        return Math.max(0, budget - prizeTotal() - spent);
    }

    /**
     * What items delivered after others are paid: the rounded price of all of them less the rounded price of those
     * before, so the deliveries of a contest add up to exactly what its whole amount costs.
     *
     * @param unit   price per item
     * @param before items delivered before
     * @param items  items delivered now
     * @return what the items are paid
     */
    public static double marginal(double unit, int before, int items) {
        return BigDecimal.valueOf(OrderBuilder.total(unit, before + items))
                .subtract(BigDecimal.valueOf(OrderBuilder.total(unit, before)))
                .doubleValue();
    }

    /**
     * @param unit   price per item
     * @param before items delivered before
     * @param wanted items wanted
     * @param money  money left
     * @return the most of the wanted items the money pays for, each delivery paid by {@link #marginal}
     */
    public static int affordableAfter(double unit, int before, int wanted, double money) {
        if (wanted <= 0) return 0;
        if (!(unit > 0)) return wanted;
        int low = 0;
        int high = wanted;
        while (low < high) {
            var middle = (int) (((long) low + high + 1) / 2);
            if (marginal(unit, before, middle) <= money + 0.000_001) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    /**
     * @param unit   price per item
     * @param wanted items wanted
     * @param budget money left
     * @return the most of the wanted items the money pays for, at the price an order rounds to
     */
    public static int affordable(double unit, int wanted, double budget) {
        if (wanted <= 0) return 0;
        if (!(unit > 0)) return wanted;
        var amount = (int) Math.min(wanted, Math.floor((budget + 0.000_001) / unit));
        while (amount > 0 && OrderBuilder.total(unit, amount) > budget + 0.000_001) amount--;
        return Math.max(0, amount);
    }

    /**
     * @return items still missing
     */
    public int missing() {
        return Math.max(0, amount - delivered);
    }

    /**
     * @return what the offer may still spend
     */
    public double budgetLeft() {
        return Math.max(0, budget - spent);
    }

    /**
     * @return whether its whole amount was delivered
     */
    public boolean reached() {
        return delivered >= amount;
    }

    /**
     * @return the share delivered, from 0 to 100
     */
    public int percent() {
        return percent(delivered, amount);
    }

    /**
     * @param delivered items delivered
     * @param amount    items asked for
     * @return the share delivered, from 0 to 100, rounded down so 100 means reached
     */
    public static int percent(int delivered, int amount) {
        if (amount <= 0) return 100;
        return (int) Math.min(100, Math.max(0, (long) delivered * 100 / amount));
    }

    /**
     * @return what players see of it next to its orders
     */
    public OfferMark mark() {
        return new OfferMark(id, name, ends, state, amount, delivered);
    }
}
