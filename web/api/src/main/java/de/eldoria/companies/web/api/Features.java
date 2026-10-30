/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
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
     * The event offers of the server: running goals and contests with their progress, the contributors of a goal, the
     * ranking and prizes of a contest, and joining or leaving a contest for the company.
     */
    public static final String EVENTS = "events";

    /**
     * Orders the server placed itself carry a badge, and the search can show or hide them. Offered only while the
     * server marks its orders.
     */
    public static final String ORDERS_SERVER_MARKS = "orders.servermarks";

    /**
     * Price statistics per item.
     */
    public static final String MARKET = "market";

    /**
     * The prefix of the admin features and actions. An admin action is checked against the admin permission of its
     * command, named on each feature.
     */
    public static final String ADMIN_PREFIX = "admin.";

    /**
     * Look up, search, unclaim, cancel, deliver and extend any order. Needs {@code companies.admin.orders}.
     */
    public static final String ADMIN_ORDERS = "admin.orders";

    /**
     * Look up a player, grant items and money. Needs {@code companies.admin.player} for the lookup and
     * {@code companies.admin.grant} for grants.
     */
    public static final String ADMIN_PLAYERS = "admin.players";

    /**
     * See and adjust a company's treasury, rename it, transfer its ownership and delete it. Needs
     * {@code companies.admin.treasury}, {@code companies.admin.rename}, {@code companies.admin.transferOwner} and
     * {@code companies.admin.delete} in that order.
     */
    public static final String ADMIN_COMPANIES = "admin.companies";

    /**
     * Edit the company levels and recalculate every company's level. Needs {@code companies.admin.level}, and
     * {@code companies.admin.calcLevel} for the recalculation.
     */
    public static final String ADMIN_LEVELS = "admin.levels";

    /**
     * List the board presets, write the board signs again and link NPCs to boards. Needs
     * {@code companies.admin.boards}.
     */
    public static final String ADMIN_BOARDS = "admin.boards";

    /**
     * List and remove the nodes registered in the database. Needs {@code companies.admin.nodes}.
     */
    public static final String ADMIN_NODES = "admin.nodes";

    /**
     * Load the configuration stored in the database on every node. Needs {@code companies.admin}.
     */
    public static final String ADMIN_CONFIG = "admin.config";

    /**
     * Read the shared settings, their history and how every server stands on them, which needs
     * {@code companies.admin}; change them and roll a file back, which needs {@code companies.admin.reload} too.
     */
    public static final String ADMIN_SETTINGS = "admin.settings";

    /**
     * Walk through the guided setup, which saves settings like the settings editor. Reading needs
     * {@code companies.admin}, saving and marking pages {@code companies.admin.reload} too.
     */
    public static final String ADMIN_SETUP = "admin.setup";

    /**
     * See the orders the server places itself, why each was placed and what guessed orders learned, and edit the rules
     * that place them. Needs {@code companies.admin.orders}, and {@code companies.admin.reload} too to change rules.
     */
    public static final String ADMIN_SYSTEM_ORDERS = "admin.systemorders";

    /**
     * See the event offers of the server with their money, progress, contributors, ranking and prizes, create goals and
     * contests, pause, resume and cancel them, change how many chunks of a goal wait at once, and settle a stuck prize.
     * Needs {@code companies.admin.offers}.
     */
    public static final String ADMIN_OFFERS = "admin.offers";

    /**
     * See the server events with their effect, scope, times, money and offers, create them, end, cancel and extend
     * them and change their budget. Needs {@code companies.admin.events}.
     */
    public static final String ADMIN_EVENTS = "admin.events";

    /**
     * Every feature name of this version.
     */
    /**
     * Post contracts and see, approve, reject, dispute and cancel the player's jobs; take, give back and finish the
     * company's jobs.
     */
    public static final String JOBS = "jobs";

    /**
     * List and settle disputes about jobs.
     */
    public static final String ADMIN_JOBS = "admin.jobs";

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
            MARKET,
            ADMIN_ORDERS,
            ADMIN_PLAYERS,
            ADMIN_COMPANIES,
            ADMIN_LEVELS,
            ADMIN_BOARDS,
            ADMIN_NODES,
            ADMIN_CONFIG,
            ADMIN_SETTINGS,
            ADMIN_SETUP,
            ADMIN_SYSTEM_ORDERS,
            ORDERS_SERVER_MARKS,
            EVENTS,
            ADMIN_OFFERS,
            ADMIN_EVENTS,
            JOBS,
            ADMIN_JOBS);

    private Features() {}
}
