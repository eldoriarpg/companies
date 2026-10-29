/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * An offer the server makes for an event or a shared goal, stored in {@code event_offers}.
 * <p>
 * A goal asks for {@code amount} items at {@code unitPrice} each and places them as system orders of
 * {@code chunkSize} items, keeping {@code openChunks} of them waiting to be taken. Its money comes from an economy
 * account or is minted, within {@code budget} for the whole offer; {@code spent} is what its open and delivered chunks
 * hold, {@code delivered} the items delivered so far. Both are kept by the chunks' changes and survive the purge of
 * their orders.
 * <p>
 * Contests (not built yet) use the same row: the line, amount, unit price and money, without chunks.
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
 * @param spent      what its open and delivered chunks hold
 * @param delivered  items delivered so far
 * @param starts     when it starts
 * @param ends       when it ends; chunks taken before may still be delivered after
 * @param state      where it is in its life
 * @param createdBy  who created it
 * @param created    when it was created
 * @param closed     when it was closed, null while it runs
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
        @Nullable LocalDateTime closed) {
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
                null);
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
