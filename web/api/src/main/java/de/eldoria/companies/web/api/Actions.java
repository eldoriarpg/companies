/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
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
     * Names the price and fee of an order {@link #ORDERS_CREATE} would place, or why it would be refused, without
     * placing it.
     */
    public static final String ORDERS_QUOTE = "orders.create.quote";

    /**
     * Returns what orders can be made of on this server: the orderable materials and what survival can give an item.
     */
    public static final String ORDERS_CATALOGUE = "orders.create.catalogue";

    /**
     * Returns the player's saved items and the exact items of their earlier orders.
     */
    public static final String ORDERS_ITEMS = "orders.create.items";

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

    /**
     * Shows any order's details by its id.
     */
    public static final String ADMIN_ORDERS_INFO = "admin.orders.info";

    /**
     * Lists the orders of a player or a company.
     */
    public static final String ADMIN_ORDERS_SEARCH = "admin.orders.search";

    /**
     * Takes a claimed order away from its company.
     */
    public static final String ADMIN_ORDERS_UNCLAIM = "admin.orders.unclaim";

    /**
     * Deletes any order, returning price and fee to its owner or nothing.
     */
    public static final String ADMIN_ORDERS_CANCEL = "admin.orders.cancel";

    /**
     * Marks a claimed order delivered.
     */
    public static final String ADMIN_ORDERS_DELIVER = "admin.orders.deliver";

    /**
     * Extends the time of any order by hours.
     */
    public static final String ADMIN_ORDERS_EXTEND = "admin.orders.extend";

    /**
     * Shows a player's company and role, orders, waiting items and latest notifications.
     */
    public static final String ADMIN_PLAYERS_LOOKUP = "admin.players.lookup";

    /**
     * Grants a player an amount of a material, collected with {@code /order collect}.
     */
    public static final String ADMIN_PLAYERS_GRANT_ITEMS = "admin.players.grant-items";

    /**
     * Deposits money to a player.
     */
    public static final String ADMIN_PLAYERS_GRANT_MONEY = "admin.players.grant-money";

    /**
     * Shows a company's treasury and its log.
     */
    public static final String ADMIN_COMPANIES_TREASURY = "admin.companies.treasury";

    /**
     * Adds money to a company's treasury.
     */
    public static final String ADMIN_COMPANIES_TREASURY_GIVE = "admin.companies.treasury.give";

    /**
     * Removes money from a company's treasury.
     */
    public static final String ADMIN_COMPANIES_TREASURY_TAKE = "admin.companies.treasury.take";

    /**
     * Renames a company free of charge.
     */
    public static final String ADMIN_COMPANIES_RENAME = "admin.companies.rename";

    /**
     * Makes another member the owner of a company.
     */
    public static final String ADMIN_COMPANIES_TRANSFER_OWNER = "admin.companies.transfer-owner";

    /**
     * Deletes a company.
     */
    public static final String ADMIN_COMPANIES_DELETE = "admin.companies.delete";

    /**
     * Lists the company levels.
     */
    public static final String ADMIN_LEVELS_LIST = "admin.levels.list";

    /**
     * Shows one level's settings.
     */
    public static final String ADMIN_LEVELS_INFO = "admin.levels.info";

    /**
     * Adds a level, at the end or at a position.
     */
    public static final String ADMIN_LEVELS_CREATE = "admin.levels.create";

    /**
     * Changes one field of a level.
     */
    public static final String ADMIN_LEVELS_EDIT = "admin.levels.edit";

    /**
     * Moves a level to another position.
     */
    public static final String ADMIN_LEVELS_MOVE = "admin.levels.move";

    /**
     * Removes a level.
     */
    public static final String ADMIN_LEVELS_REMOVE = "admin.levels.remove";

    /**
     * Recalculates the level of every company.
     */
    public static final String ADMIN_LEVELS_RECALCULATE = "admin.levels.recalculate";

    /**
     * Lists the board presets with their open orders.
     */
    public static final String ADMIN_BOARDS_PRESETS = "admin.boards.presets";

    /**
     * Writes every loaded board sign again.
     */
    public static final String ADMIN_BOARDS_RELOAD = "admin.boards.reload";

    /**
     * Lists the NPCs linked to boards.
     */
    public static final String ADMIN_BOARDS_NPC_LIST = "admin.boards.npc.list";

    /**
     * Links an NPC to a board.
     */
    public static final String ADMIN_BOARDS_NPC_LINK = "admin.boards.npc.link";

    /**
     * Removes an NPC's link to a board.
     */
    public static final String ADMIN_BOARDS_NPC_UNLINK = "admin.boards.npc.unlink";

    /**
     * Lists the nodes registered in the database.
     */
    public static final String ADMIN_NODES_LIST = "admin.nodes.list";

    /**
     * Removes a node from the database.
     */
    public static final String ADMIN_NODES_REMOVE = "admin.nodes.remove";

    /**
     * Loads the configuration stored in the database on every node.
     */
    public static final String ADMIN_CONFIG_SYNC = "admin.config.sync";

    /**
     * Reads a shared settings file as its latest revision, with how the servers stand on it.
     */
    public static final String ADMIN_SETTINGS_GET = "admin.settings.get";

    /**
     * Lists a page of the revisions of a shared settings file, newest first.
     */
    public static final String ADMIN_SETTINGS_HISTORY = "admin.settings.history";

    /**
     * Reads what one revision of a shared settings file changed against the one before.
     */
    public static final String ADMIN_SETTINGS_REVISION = "admin.settings.revision";

    /**
     * Stores changed settings of a shared settings file as its next revision.
     */
    public static final String ADMIN_SETTINGS_SAVE = "admin.settings.save";

    /**
     * Makes an earlier revision of a shared settings file the newest again.
     */
    public static final String ADMIN_SETTINGS_ROLLBACK = "admin.settings.rollback";

    /**
     * Uploads a PNG as the dashboard icon of every server.
     */
    public static final String ADMIN_SETTINGS_ICON = "admin.settings.icon";

    /**
     * Reads the pages of the guided setup and how far it got.
     */
    public static final String ADMIN_SETUP_GET = "admin.setup.get";

    /**
     * Notes a setup page as done, skipped or open again, or the setup reminder as dismissed.
     */
    public static final String ADMIN_SETUP_MARK = "admin.setup.mark";

    /**
     * Lists the orders the server placed itself that are open or in work.
     */
    public static final String ADMIN_SYSTEM_ORDERS_LIST = "admin.systemorders.list";

    /**
     * Reads why the server placed one of its orders.
     */
    public static final String ADMIN_SYSTEM_ORDERS_INFO = "admin.systemorders.info";

    /**
     * Reads what the server's guessed orders learned per material.
     */
    public static final String ADMIN_SYSTEM_ORDERS_STATS = "admin.systemorders.stats";

    /**
     * Reads the defined rules of the server's own orders, as the latest revision has them.
     */
    public static final String ADMIN_SYSTEM_ORDERS_RULES = "admin.systemorders.rules";

    /**
     * Stores the defined rules of the server's own orders as the next revision of their file.
     */
    public static final String ADMIN_SYSTEM_ORDERS_RULES_SAVE = "admin.systemorders.rules.save";

    /**
     * Lists the event offers a player sees: running or paused, and those starting within a day.
     */
    public static final String EVENTS_LIST = "events.list";

    /**
     * Reads an event offer: the top contributors of a goal, the ranking, prizes and own place of a contest.
     */
    public static final String EVENTS_INFO = "events.info";

    /**
     * Lets the player's company take part in a contest.
     */
    public static final String EVENTS_JOIN = "events.join";

    /**
     * Lets the player's company leave a contest; it keeps its place.
     */
    public static final String EVENTS_LEAVE = "events.leave";

    /**
     * Lists the newest event offers with their state, progress and money.
     */
    public static final String ADMIN_OFFERS_LIST = "admin.offers.list";

    /**
     * Reads an event offer as admins see it: who created it, its money, contributors, ranking and prizes.
     */
    public static final String ADMIN_OFFERS_INFO = "admin.offers.info";

    /**
     * Creates a goal or a contest.
     */
    public static final String ADMIN_OFFERS_CREATE = "admin.offers.create";

    /**
     * Pauses a running event offer.
     */
    public static final String ADMIN_OFFERS_PAUSE = "admin.offers.pause";

    /**
     * Resumes a paused event offer.
     */
    public static final String ADMIN_OFFERS_RESUME = "admin.offers.resume";

    /**
     * Closes an event offer for good.
     */
    public static final String ADMIN_OFFERS_CANCEL = "admin.offers.cancel";

    /**
     * Changes how many chunks of a goal wait to be taken at once.
     */
    public static final String ADMIN_OFFERS_OPEN = "admin.offers.open";

    /**
     * Settles a contest prize whose payment stopped without a known outcome.
     */
    public static final String ADMIN_OFFERS_PRIZE = "admin.offers.prize";

    /**
     * Lists the newest server events with their effect, scope, times, money and offers.
     */
    public static final String ADMIN_EVENTS_LIST = "admin.events.list";

    /**
     * Creates a server event from the fields of the command's form.
     */
    public static final String ADMIN_EVENTS_CREATE = "admin.events.create";

    /**
     * Ends a running server event now.
     */
    public static final String ADMIN_EVENTS_END = "admin.events.end";

    /**
     * Cancels a waiting or running server event.
     */
    public static final String ADMIN_EVENTS_CANCEL = "admin.events.cancel";

    /**
     * Moves the end of a waiting or running server event later.
     */
    public static final String ADMIN_EVENTS_EXTEND = "admin.events.extend";

    /**
     * Sets or removes the budget of a reward multiplier.
     */
    public static final String ADMIN_EVENTS_BUDGET = "admin.events.budget";

    /**
     * Lists the open jobs, the player's own jobs and those of their company.
     */
    public static final String JOBS_LIST = "jobs.list";

    /**
     * Reads a job the player may see: open, their own or their company's.
     */
    public static final String JOBS_INFO = "jobs.info";

    /**
     * Posts a contract, a job described in words; its price and fee are taken at once.
     */
    public static final String JOBS_CONTRACT = "jobs.contract";

    /**
     * Describes the player's job while it waits for a company.
     */
    public static final String JOBS_DESCRIBE = "jobs.describe";

    /**
     * Approves the player's job and pays the company.
     */
    public static final String JOBS_APPROVE = "jobs.approve";

    /**
     * Sends a contract the company says is done back to work.
     */
    public static final String JOBS_REJECT = "jobs.reject";

    /**
     * Opens a dispute about a job as its owner or for the player's company.
     */
    public static final String JOBS_DISPUTE = "jobs.dispute";

    /**
     * Withdraws a dispute the player or their company opened.
     */
    public static final String JOBS_WITHDRAW = "jobs.withdraw";

    /**
     * Cancels the player's job while nobody works on it.
     */
    public static final String JOBS_CANCEL = "jobs.cancel";

    /**
     * Ends a harvest job, as its owner or for the player's company.
     */
    public static final String JOBS_END = "jobs.end";

    /**
     * Takes an open job for the player's company.
     */
    public static final String JOBS_TAKE = "jobs.take";

    /**
     * Gives a job of the player's company back.
     */
    public static final String JOBS_GIVE_BACK = "jobs.giveback";

    /**
     * Tells the owner the company's contract is done.
     */
    public static final String JOBS_DONE = "jobs.done";

    /**
     * Gives the company working on the player's job more time.
     */
    public static final String JOBS_EXTEND = "jobs.extend";

    /**
     * Asks the owner of a job of the player's company for more time.
     */
    public static final String JOBS_EXTENSION_ASK = "jobs.extension.ask";

    /**
     * Grants the request of the company working on the player's job for more time.
     */
    public static final String JOBS_EXTENSION_GRANT = "jobs.extension.grant";

    /**
     * Refuses the request of the company working on the player's job for more time.
     */
    public static final String JOBS_EXTENSION_REFUSE = "jobs.extension.refuse";

    /**
     * Lists the disputed jobs with both sides and the reason.
     */
    public static final String ADMIN_JOBS_DISPUTES = "admin.jobs.disputes";

    /**
     * Settles a dispute with a share of the price for the company.
     */
    public static final String ADMIN_JOBS_SETTLE = "admin.jobs.settle";

    /**
     * Every action name of this version.
     */
    public static final List<String> ALL = List.of(
            ORDERS_SEARCH,
            ORDERS_DETAILS,
            ORDERS_ACCEPT,
            ORDERS_ABORT,
            ORDERS_CREATE,
            ORDERS_QUOTE,
            ORDERS_CATALOGUE,
            ORDERS_ITEMS,
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
            MARKET_MOVERS,
            ADMIN_ORDERS_INFO,
            ADMIN_ORDERS_SEARCH,
            ADMIN_ORDERS_UNCLAIM,
            ADMIN_ORDERS_CANCEL,
            ADMIN_ORDERS_DELIVER,
            ADMIN_ORDERS_EXTEND,
            ADMIN_PLAYERS_LOOKUP,
            ADMIN_PLAYERS_GRANT_ITEMS,
            ADMIN_PLAYERS_GRANT_MONEY,
            ADMIN_COMPANIES_TREASURY,
            ADMIN_COMPANIES_TREASURY_GIVE,
            ADMIN_COMPANIES_TREASURY_TAKE,
            ADMIN_COMPANIES_RENAME,
            ADMIN_COMPANIES_TRANSFER_OWNER,
            ADMIN_COMPANIES_DELETE,
            ADMIN_LEVELS_LIST,
            ADMIN_LEVELS_INFO,
            ADMIN_LEVELS_CREATE,
            ADMIN_LEVELS_EDIT,
            ADMIN_LEVELS_MOVE,
            ADMIN_LEVELS_REMOVE,
            ADMIN_LEVELS_RECALCULATE,
            ADMIN_BOARDS_PRESETS,
            ADMIN_BOARDS_RELOAD,
            ADMIN_BOARDS_NPC_LIST,
            ADMIN_BOARDS_NPC_LINK,
            ADMIN_BOARDS_NPC_UNLINK,
            ADMIN_NODES_LIST,
            ADMIN_NODES_REMOVE,
            ADMIN_CONFIG_SYNC,
            ADMIN_SETTINGS_GET,
            ADMIN_SETTINGS_HISTORY,
            ADMIN_SETTINGS_REVISION,
            ADMIN_SETTINGS_SAVE,
            ADMIN_SETTINGS_ROLLBACK,
            ADMIN_SETTINGS_ICON,
            ADMIN_SETUP_GET,
            ADMIN_SETUP_MARK,
            ADMIN_SYSTEM_ORDERS_LIST,
            ADMIN_SYSTEM_ORDERS_INFO,
            ADMIN_SYSTEM_ORDERS_STATS,
            ADMIN_SYSTEM_ORDERS_RULES,
            ADMIN_SYSTEM_ORDERS_RULES_SAVE,
            EVENTS_LIST,
            EVENTS_INFO,
            EVENTS_JOIN,
            EVENTS_LEAVE,
            ADMIN_OFFERS_LIST,
            ADMIN_OFFERS_INFO,
            ADMIN_OFFERS_CREATE,
            ADMIN_OFFERS_PAUSE,
            ADMIN_OFFERS_RESUME,
            ADMIN_OFFERS_CANCEL,
            ADMIN_OFFERS_OPEN,
            ADMIN_OFFERS_PRIZE,
            ADMIN_EVENTS_LIST,
            ADMIN_EVENTS_CREATE,
            ADMIN_EVENTS_END,
            ADMIN_EVENTS_CANCEL,
            ADMIN_EVENTS_EXTEND,
            ADMIN_EVENTS_BUDGET,
            JOBS_LIST,
            JOBS_INFO,
            JOBS_CONTRACT,
            JOBS_DESCRIBE,
            JOBS_APPROVE,
            JOBS_REJECT,
            JOBS_DISPUTE,
            JOBS_WITHDRAW,
            JOBS_CANCEL,
            JOBS_END,
            JOBS_TAKE,
            JOBS_GIVE_BACK,
            JOBS_DONE,
            JOBS_EXTEND,
            JOBS_EXTENSION_ASK,
            JOBS_EXTENSION_GRANT,
            JOBS_EXTENSION_REFUSE,
            ADMIN_JOBS_DISPUTES,
            ADMIN_JOBS_SETTLE);

    private Actions() {}

    /**
     * Finds the feature an action belongs to: the longest feature name the action name starts with.
     *
     * @param action the action name
     * @return the feature, none for an action of no known feature
     */
    public static @Nullable String feature(String action) {
        return Features.ALL.stream()
                .filter(feature -> action.equals(feature) || action.startsWith(feature + "."))
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
    }
}
