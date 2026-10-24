/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api;

import de.eldoria.companies.web.api.action.ActionRequest;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * The action names of {@link ActionRequest}. Every action name starts with the name of the feature it belongs to, so
 * the plugin can refuse an action whose feature is switched off and the web app hides it when the feature is not
 * listed. A feature's main action may carry the feature name itself. Like features, a released action name keeps its
 * meaning forever.
 */
public final class Actions {
    /**
     * Searches orders with filters and sorting; returns a page of results.
     */
    public static final String ORDERS_SEARCH = "orders.browse.search";

    /**
     * Shows one order's details.
     */
    public static final String ORDERS_DETAILS = "orders.browse.details";

    /**
     * Accepts an order for the company.
     */
    public static final String ORDERS_ACCEPT = "orders.accept";

    /**
     * Aborts an order the company accepted.
     */
    public static final String ORDERS_ABORT = "orders.accept.abort";

    /**
     * Places an order.
     */
    public static final String ORDERS_CREATE = "orders.create";

    /**
     * Lists the player's own orders, paged.
     */
    public static final String ORDERS_OWN_LIST = "orders.own.list";

    /**
     * Cancels an own order.
     */
    public static final String ORDERS_OWN_CANCEL = "orders.own.cancel";

    /**
     * Extends the time of an own order.
     */
    public static final String ORDERS_OWN_EXTEND = "orders.own.extend";

    /**
     * Raises the payment of an own order.
     */
    public static final String ORDERS_OWN_RAISE = "orders.own.raise";

    /**
     * Marks delivered items to be handed out on the next join or with {@code /order collect}.
     */
    public static final String ORDERS_RECEIVE = "orders.receive";

    /**
     * Delivers one line of a claimed order from the player's inventory.
     */
    public static final String ORDERS_DELIVER_LINE = "orders.deliver.line";

    /**
     * Delivers everything the player carries for a claimed order.
     */
    public static final String ORDERS_DELIVER_ALL = "orders.deliver.all";

    /**
     * Receives the items of a delivered own order into the inventory.
     */
    public static final String ORDERS_DELIVER_RECEIVE = "orders.deliver.receive";

    /**
     * Collects returned items into the inventory.
     */
    public static final String ORDERS_DELIVER_COLLECT = "orders.deliver.collect";

    /**
     * Revokes an invite. Inviting a player stays in game.
     */
    public static final String MEMBERS_REVOKE = "company.members.revoke";

    /**
     * Removes a member.
     */
    public static final String MEMBERS_KICK = "company.members.kick";

    /**
     * Leaves the company.
     */
    public static final String MEMBERS_LEAVE = "company.members.leave";

    /**
     * Changes a member's permissions.
     */
    public static final String MEMBERS_PERMISSIONS = "company.members.permissions";

    /**
     * Transfers the ownership to another member.
     */
    public static final String MEMBERS_TRANSFER = "company.members.transfer";

    /**
     * Lists pending applications.
     */
    public static final String APPLICATIONS_LIST = "company.applications.list";

    /**
     * Accepts an application.
     */
    public static final String APPLICATIONS_ACCEPT = "company.applications.accept";

    /**
     * Denies an application.
     */
    public static final String APPLICATIONS_DENY = "company.applications.deny";

    /**
     * Sets how players join the company.
     */
    public static final String APPLICATIONS_JOIN_MODE = "company.applications.join-mode";

    /**
     * Sets the description.
     */
    public static final String PROFILE_DESCRIPTION = "company.profile.description";

    /**
     * Sets the tag.
     */
    public static final String PROFILE_TAG = "company.profile.tag";

    /**
     * Sets the colour.
     */
    public static final String PROFILE_COLOR = "company.profile.color";

    /**
     * Renames the company, paid from the treasury or by the player.
     */
    public static final String PROFILE_RENAME = "company.profile.rename";

    /**
     * Pages through the treasury's log.
     */
    public static final String TREASURY_LOG = "company.treasury.log";

    /**
     * Deposits money into the treasury.
     */
    public static final String TREASURY_DEPOSIT = "company.treasury.deposit";

    /**
     * Withdraws money from the treasury.
     */
    public static final String TREASURY_WITHDRAW = "company.treasury.withdraw";

    /**
     * Changes the treasury's share of payments.
     */
    public static final String TREASURY_SHARE = "company.treasury.share";

    /**
     * Founds a company.
     */
    public static final String COMPANY_CREATE = "company.create";

    /**
     * Disbands the company.
     */
    public static final String COMPANY_DISBAND = "company.disband";

    /**
     * Returns a page of the inbox.
     */
    public static final String INBOX_PAGE = "inbox.page";

    /**
     * Marks every inbox entry as read.
     */
    public static final String INBOX_READ_ALL = "inbox.read-all";

    /**
     * Clears the inbox.
     */
    public static final String INBOX_CLEAR = "inbox.clear";

    /**
     * Mutes or unmutes a kind of inbox entry.
     */
    public static final String INBOX_MUTE = "inbox.mute";

    /**
     * Returns a page of a ranking.
     */
    public static final String RANKINGS_PAGE = "rankings.page";

    /**
     * Returns the company's own place in a ranking.
     */
    public static final String RANKINGS_OWN = "rankings.own";

    /**
     * Returns the company's places over time.
     */
    public static final String RANKINGS_HISTORY = "rankings.history";

    /**
     * Returns the price statistics of one item.
     */
    public static final String MARKET_ITEM = "market.item";

    /**
     * Searches items by name.
     */
    public static final String MARKET_SEARCH = "market.search";

    /**
     * Returns the most ordered items.
     */
    public static final String MARKET_TOP = "market.top";

    /**
     * Returns the items whose price moved most.
     */
    public static final String MARKET_MOVERS = "market.movers";

    // TODO: admin action names, one per admin tool
    /**
     * Every action name of this version except the admin actions.
     */
    public static final List<String> ALL = List.of(
            ORDERS_SEARCH,
            ORDERS_DETAILS,
            ORDERS_ACCEPT,
            ORDERS_ABORT,
            ORDERS_CREATE,
            ORDERS_OWN_LIST,
            ORDERS_OWN_CANCEL,
            ORDERS_OWN_EXTEND,
            ORDERS_OWN_RAISE,
            ORDERS_RECEIVE,
            ORDERS_DELIVER_LINE,
            ORDERS_DELIVER_ALL,
            ORDERS_DELIVER_RECEIVE,
            ORDERS_DELIVER_COLLECT,
            MEMBERS_REVOKE,
            MEMBERS_KICK,
            MEMBERS_LEAVE,
            MEMBERS_PERMISSIONS,
            MEMBERS_TRANSFER,
            APPLICATIONS_LIST,
            APPLICATIONS_ACCEPT,
            APPLICATIONS_DENY,
            APPLICATIONS_JOIN_MODE,
            PROFILE_DESCRIPTION,
            PROFILE_TAG,
            PROFILE_COLOR,
            PROFILE_RENAME,
            TREASURY_LOG,
            TREASURY_DEPOSIT,
            TREASURY_WITHDRAW,
            TREASURY_SHARE,
            COMPANY_CREATE,
            COMPANY_DISBAND,
            INBOX_PAGE,
            INBOX_READ_ALL,
            INBOX_CLEAR,
            INBOX_MUTE,
            RANKINGS_PAGE,
            RANKINGS_OWN,
            RANKINGS_HISTORY,
            MARKET_ITEM,
            MARKET_SEARCH,
            MARKET_TOP,
            MARKET_MOVERS);

    private Actions() {}

    /**
     * Finds the feature an action belongs to: the longest feature name the action name starts with, or for an admin
     * action its first two segments.
     *
     * @param action the action name
     * @return the feature, none for an action of no known feature
     */
    public static @Nullable String feature(String action) {
        if (action.startsWith(Features.ADMIN_PREFIX)) {
            int end = action.indexOf('.', Features.ADMIN_PREFIX.length());
            return end < 0 ? action : action.substring(0, end);
        }
        return Features.ALL.stream()
                .filter(feature -> action.equals(feature) || action.startsWith(feature + "."))
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
    }
}
