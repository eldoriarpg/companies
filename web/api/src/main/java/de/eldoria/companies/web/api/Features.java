/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api;

import java.util.List;

/**
 * The feature names a node lists in its hello. The web app shows a page or a button only when its feature is listed,
 * so a server on an older plugin simply shows less.
 * <p>
 * A released feature name keeps its meaning forever. A changed meaning gets a new name, such as
 * {@code orders.create.v2}, and the old one is marked {@link DeprecatedSince}.
 */
public final class Features {
    /**
     * Search orders with filters and sorting, and see their details.
     */
    public static final String ORDERS_BROWSE = "orders.browse";

    /**
     * Accept orders for the company and abort them.
     */
    public static final String ORDERS_ACCEPT = "orders.accept";

    /**
     * Place orders from material lines, earlier orders' items and saved items.
     */
    public static final String ORDERS_CREATE = "orders.create";

    /**
     * List, cancel, extend and raise the player's own orders.
     */
    public static final String ORDERS_OWN = "orders.own";

    /**
     * Mark delivered items to be handed out on the next join.
     */
    public static final String ORDERS_RECEIVE = "orders.receive";

    /**
     * Deliver, receive and collect items; works only while the player is online.
     */
    public static final String ORDERS_DELIVER = "orders.deliver";

    /**
     * Manage members: revoke invites, kick, leave, edit permissions, transfer ownership.
     */
    public static final String COMPANY_MEMBERS = "company.members";

    /**
     * Review applications and set the join mode.
     */
    public static final String COMPANY_APPLICATIONS = "company.applications";

    /**
     * Edit description, tag and colour, and rename the company.
     */
    public static final String COMPANY_PROFILE = "company.profile";

    /**
     * See the treasury's log, deposit, withdraw and change the share.
     */
    public static final String COMPANY_TREASURY = "company.treasury";

    /**
     * Found a company.
     */
    public static final String COMPANY_CREATE = "company.create";

    /**
     * Disband the company.
     */
    public static final String COMPANY_DISBAND = "company.disband";

    /**
     * Page through the inbox, mark it read, clear and mute it.
     */
    public static final String INBOX = "inbox";

    /**
     * Ranking pages, the own rank and its history.
     */
    public static final String RANKINGS = "rankings";

    /**
     * Price statistics per item.
     */
    public static final String MARKET = "market";

    /**
     * The prefix of the admin features, one per admin permission, e.g. {@code admin.orders}.
     */
    public static final String ADMIN_PREFIX = "admin.";

    /**
     * Every feature name of this version except the admin features.
     */
    public static final List<String> ALL = List.of(
            ORDERS_BROWSE,
            ORDERS_ACCEPT,
            ORDERS_CREATE,
            ORDERS_OWN,
            ORDERS_RECEIVE,
            ORDERS_DELIVER,
            COMPANY_MEMBERS,
            COMPANY_APPLICATIONS,
            COMPANY_PROFILE,
            COMPANY_TREASURY,
            COMPANY_CREATE,
            COMPANY_DISBAND,
            INBOX,
            RANKINGS,
            MARKET);

    private Features() {}
}
