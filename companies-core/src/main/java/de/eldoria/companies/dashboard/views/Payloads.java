/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.views;

import de.eldoria.companies.web.api.session.PlayerRef;
import de.eldoria.companies.web.api.snapshot.ItemView;
import de.eldoria.companies.web.api.snapshot.OrderLineView;
import de.eldoria.companies.web.api.snapshot.OrderState;
import de.eldoria.companies.web.api.snapshot.RankingEntry;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The data of the read actions, which the protocol module does not model as records. It is written as JSON into
 * {@code action.result.data}; like every payload it only gains fields. Paged data always carries {@code page},
 * {@code pages} and {@code total}.
 */
public final class Payloads {
    private Payloads() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * An order as one player may see it. Neither its owner nor the company or worker on it are ever part of it.
     *
     * @param id      the order id
     * @param name    the order's name
     * @param state   its state
     * @param lines   its lines with progress
     * @param created when it was placed
     * @param deadline when the claim runs out, none unless claimed
     * @param expires  when an unclaimed order is removed, none once claimed
     * @param price   the price of all lines
     * @param amount  the number of ordered items
     * @param claimed when it was claimed, for the company working on it
     * @param waiting items delivered and waiting for the owner, for the owner of a delivered order
     */
    public record OrderSummary(
            int id,
            String name,
            OrderState state,
            List<OrderLineView> lines,
            Instant created,
            @Nullable Instant deadline,
            @Nullable Instant expires,
            double price,
            int amount,
            @Nullable Instant claimed,
            @Nullable List<ItemView> waiting) {}

    /**
     * A page of orders.
     *
     * @param page   the page, starting at 1
     * @param pages  the number of pages, at least 1
     * @param total  the number of orders on all pages
     * @param orders the orders of the page
     */
    public record OrderPage(int page, int pages, int total, List<OrderSummary> orders) {}

    /**
     * A page of search results.
     *
     * @param page         the page, starting at 1
     * @param pages        the number of pages, at least 1
     * @param total        the number of results on all pages
     * @param taxPercent   the tax taken from payments, 5 meaning 5 %
     * @param canAccept    whether the player may accept orders, for the company or alone
     * @param limitReached whether the company or player works on as many orders as allowed
     * @param orders       the orders of the page
     */
    public record SearchPage(
            int page,
            int pages,
            int total,
            double taxPercent,
            boolean canAccept,
            boolean limitReached,
            List<OrderSummary> orders) {}

    /**
     * A page of entries.
     *
     * @param page    the page, starting at 1
     * @param pages   the number of pages, at least 1
     * @param total   the number of entries on all pages
     * @param entries the entries of the page
     * @param <T>     the entry type
     */
    public record Page<T>(int page, int pages, int total, List<T> entries) {}

    /**
     * A page of the inbox with the categories the player muted and the ones that can be muted.
     *
     * @param page     the page, starting at 1
     * @param pages    the number of pages, at least 1
     * @param total    the number of entries on all pages
     * @param entries  the entries of the page
     * @param muted    the muted categories
     * @param mutable  the categories that can be muted
     */
    public record InboxPage(
            int page, int pages, int total, List<InboxEntry> entries, List<String> muted, List<String> mutable) {}

    /**
     * An inbox entry rendered as the player reads it.
     *
     * @param id       the entry id
     * @param type     the kind of notification, the name of the plugin's notification type
     * @param category the category it is muted by
     * @param created  when it was sent
     * @param read     whether it was read
     * @param text     the message as plain text
     */
    public record InboxEntry(long id, String type, String category, Instant created, boolean read, String text) {}

    /**
     * A transaction of the treasury log.
     *
     * @param id      the entry id
     * @param time    when it happened
     * @param kind    what moved the money
     * @param amount  the signed amount
     * @param balance the balance after it
     * @param actor   the member or admin who caused it, none for payout shares and system actions
     * @param order   the company's order of a payout share
     * @param note    free text such as a new name
     */
    public record TreasuryEntry(
            long id,
            Instant time,
            String kind,
            double amount,
            double balance,
            @Nullable PlayerRef actor,
            @Nullable Integer order,
            @Nullable String note) {}

    /**
     * An open application to the company.
     *
     * @param player  the applicant
     * @param message the applicant's message
     * @param created when it was sent
     * @param expires when it expires
     */
    public record Application(PlayerRef player, String message, Instant created, Instant expires) {}

    /**
     * The open applications to a company and the invites it sent, for members who may invite.
     *
     * @param page    always 1, all are listed
     * @param pages   always 1
     * @param total   the number of applications
     * @param entries the applications, oldest first
     * @param invites the invites the company sent that are still open
     */
    public record Applications(int page, int pages, int total, List<Application> entries, List<SentInvite> invites) {}

    /**
     * An invite a company sent that is still open.
     *
     * @param player  the invited player
     * @param inviter the member who sent it
     * @param created when it was sent or last renewed
     * @param expires when it expires
     */
    public record SentInvite(PlayerRef player, PlayerRef inviter, Instant created, Instant expires) {}

    /**
     * The player's place in a ranking.
     *
     * @param ranking the ranking's key
     * @param range   the range's key
     * @param company the company id, none without a company
     * @param entry   the place, none when the company is not ranked
     */
    public record OwnRank(
            String ranking,
            String range,
            @Nullable Integer company,
            @Nullable RankingEntry entry) {}

    /**
     * The market statistics of one material. The prices are left out while fewer orders than the server's minimum
     * delivered the material, so a single order cannot be traced back.
     *
     * @param item   the material
     * @param avg    the average unit price
     * @param min    the lowest unit price
     * @param max    the highest unit price
     * @param volume the number of items ordered
     * @param orders the number of delivered orders
     * @param change the change of the average unit price in percent over the last week against the week before, absent
     *               while either week stands on too few orders
     */
    public record MarketItem(
            ItemView item,
            @Nullable Double avg,
            @Nullable Double min,
            @Nullable Double max,
            long volume,
            int orders,
            @Nullable Double change) {}

    /**
     * The market statistics of one material with its price history: the fields of {@link MarketItem}, then the
     * history.
     *
     * @param item    the material
     * @param avg     the average unit price
     * @param min     the lowest unit price
     * @param max     the highest unit price
     * @param volume  the number of items ordered
     * @param orders  the number of delivered orders
     * @param change  the change of the average unit price in percent, as in {@link MarketItem}
     * @param days    how many days the history reaches back, today included
     * @param history one point per day with delivered orders, oldest first
     */
    public record MarketDetail(
            ItemView item,
            @Nullable Double avg,
            @Nullable Double min,
            @Nullable Double max,
            long volume,
            int orders,
            @Nullable Double change,
            int days,
            List<HistoryPoint> history) {
        /**
         * @param item    the statistics
         * @param days    how many days the history reaches back
         * @param history the history
         */
        public MarketDetail(MarketItem item, int days, List<HistoryPoint> history) {
            this(
                    item.item(),
                    item.avg(),
                    item.min(),
                    item.max(),
                    item.volume(),
                    item.orders(),
                    item.change(),
                    days,
                    history);
        }
    }

    /**
     * One day of a material's price history. The prices are left out on days with fewer orders than the server's
     * minimum.
     *
     * @param day    the day, as {@code yyyy-MM-dd}
     * @param orders the orders delivered that day
     * @param items  the items of the material in them
     * @param avg    the average unit price
     * @param min    the lowest unit price
     * @param max    the highest unit price
     */
    public record HistoryPoint(
            String day,
            int orders,
            long items,
            @Nullable Double avg,
            @Nullable Double min,
            @Nullable Double max) {}

    /**
     * A page of market items.
     *
     * @param page  the page, starting at 1
     * @param pages the number of pages, at least 1
     * @param total the number of items on all pages
     * @param items the items of the page
     */
    public record MarketPage(int page, int pages, int total, List<MarketItem> items) {}

    /**
     * The number of pages of a list.
     *
     * @param total the number of entries
     * @param size  the entries per page
     * @return the pages, at least 1
     */
    public static int pages(int total, int size) {
        return Math.max(1, (total + size - 1) / size);
    }

    /**
     * One page of a list.
     *
     * @param all  the whole list
     * @param page the page, starting at 1
     * @param size the entries per page
     * @param <T>  the entry type
     * @return the entries of the page
     */
    public static <T> List<T> slice(List<T> all, int page, int size) {
        return all.stream().skip((long) (page - 1) * size).limit(size).toList();
    }
}
