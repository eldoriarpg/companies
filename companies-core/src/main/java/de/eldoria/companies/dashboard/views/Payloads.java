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
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
     * @param to      the server's today, the last day of the history, as {@code yyyy-MM-dd}
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
            String to,
            List<HistoryPoint> history) {
        /**
         * @param item    the statistics
         * @param days    how many days the history reaches back
         * @param to      the last day of the history
         * @param history the history
         */
        public MarketDetail(MarketItem item, int days, String to, List<HistoryPoint> history) {
            this(
                    item.item(),
                    item.avg(),
                    item.min(),
                    item.max(),
                    item.volume(),
                    item.orders(),
                    item.change(),
                    days,
                    to,
                    history);
        }
    }

    /**
     * One day of a material's price history. The price is left out on days with fewer orders than the server's
     * minimum.
     *
     * @param day    the day, as {@code yyyy-MM-dd}
     * @param orders the orders delivered that day
     * @param items  the items of the material in them
     * @param avg    the average unit price
     */
    public record HistoryPoint(
            String day, int orders, long items, @Nullable Double avg) {}

    /**
     * What orders can be made of on this server.
     *
     * @param materials    the orderable materials with what each can be given
     * @param enchantments the enchantments with their highest survival level and those they exclude
     * @param potions      the potion types
     * @param dyes         the dye colours a leather item can take
     * @param trimPatterns the armour trim patterns
     * @param trimMaterials the armour trim materials
     */
    public record Catalogue(
            List<CatalogueMaterial> materials,
            List<CatalogueEnchantment> enchantments,
            List<CatalogueEntry> potions,
            List<CatalogueDye> dyes,
            List<CatalogueEntry> trimPatterns,
            List<CatalogueEntry> trimMaterials) {}

    /**
     * An orderable material.
     *
     * @param item         the material
     * @param enchantments the keys of the enchantments it can take; every enchantment for an enchanted book
     * @param potion       whether it takes a potion type
     * @param dye          whether it can be dyed
     * @param trim         whether it takes an armour trim
     */
    public record CatalogueMaterial(
            ItemView item, List<String> enchantments, boolean potion, boolean dye, boolean trim) {}

    /**
     * An enchantment.
     *
     * @param key       its key
     * @param name      its English name
     * @param max       its highest level in survival
     * @param conflicts the keys of the enchantments it excludes
     */
    public record CatalogueEnchantment(String key, String name, int max, List<String> conflicts) {}

    /**
     * An entry of a registry.
     *
     * @param key  its key
     * @param name its English name
     */
    public record CatalogueEntry(String key, String name) {}

    /**
     * A dye colour.
     *
     * @param key   the dye's key, e.g. {@code red}
     * @param name  the dye's display name, e.g. {@code Red}
     * @param color the colour a leather item takes from it, as {@code #rrggbb}
     */
    public record CatalogueDye(String key, String name, String color) {}

    /**
     * An item the player can order again exactly.
     *
     * @param ref     how {@code orders.create} names it: {@code saved:<id>} or {@code earlier:<history entry>:<line>}
     * @param label   the saved item's name, or the earlier order's name
     * @param item    the item
     * @param details what it carries, such as enchantments, as short lines
     */
    public record KnownItem(String ref, String label, ItemView item, List<String> details) {}

    /**
     * The items a player can order again exactly.
     *
     * @param saved   the player's saved items
     * @param earlier the exact items of the player's earlier orders, newest first, each once
     */
    public record KnownItems(List<KnownItem> saved, List<KnownItem> earlier) {}

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

    /**
     * An order as an admin sees it, whoever owns or works on it.
     *
     * @param id              the order id
     * @param name            the order's name
     * @param state           its state
     * @param owner           its owner
     * @param company         the company working on it, none while open or worked on alone
     * @param soloWorker      the player working on it alone, none otherwise
     * @param created         when it was placed
     * @param deadline        when it runs out, none once delivered
     * @param removal         whether the open order is removed at the deadline rather than being due
     * @param price           the price of all lines
     * @param fee             the fee paid with it
     * @param lines           its lines with what each worker delivered
     * @param refundOnCancel  what cancelling with a refund gives the owner back
     * @param keptOnCancel    what cancelling without a refund hands to the collector
     * @param refundOnDeliver what marking it delivered refunds the owner for the missing items
     */
    public record AdminOrder(
            int id,
            String name,
            String state,
            PlayerRef owner,
            @Nullable String company,
            @Nullable String soloWorker,
            Instant created,
            @Nullable Instant deadline,
            boolean removal,
            double price,
            double fee,
            List<AdminOrderLine> lines,
            double refundOnCancel,
            double keptOnCancel,
            double refundOnDeliver) {}

    /**
     * One line of an order as an admin sees it.
     *
     * @param item      the ordered item
     * @param delivered how many were delivered
     * @param price     the price of the line
     * @param workers   who delivered how many, by name
     */
    public record AdminOrderLine(ItemView item, int delivered, double price, Map<String, Integer> workers) {}

    /**
     * An order of a page of an admin search.
     *
     * @param id       the order id
     * @param name     the order's name
     * @param state    its state
     * @param owner    name of the owner
     * @param claimant name of the company or the player working on it, none while open
     * @param created  when it was placed
     */
    public record AdminOrderRow(
            int id,
            String name,
            String state,
            String owner,
            @Nullable String claimant,
            Instant created) {}

    /**
     * A page of an admin search.
     *
     * @param target  the player or company searched for
     * @param page    the page, from one
     * @param hasNext whether another page may follow
     * @param orders  the orders of the page
     */
    public record AdminOrderPage(String target, int page, boolean hasNext, List<AdminOrderRow> orders) {}

    /**
     * A player as an admin looks them up.
     *
     * @param player      the player
     * @param company     the player's company, none without one
     * @param owner       whether the player owns it
     * @param permissions the player's permissions in it
     * @param orders      the player's own orders
     * @param workerItems items waiting for the player as a worker
     * @param ownerItems  items waiting for the player as an owner
     * @param unread      unread inbox entries
     * @param latest      the latest inbox entries
     */
    public record AdminPlayer(
            PlayerRef player,
            @Nullable String company,
            boolean owner,
            List<String> permissions,
            List<AdminOrderRow> orders,
            int workerItems,
            int ownerItems,
            int unread,
            List<InboxEntry> latest) {}

    /**
     * A company's treasury as an admin sees it.
     *
     * @param company the company's name
     * @param balance the balance
     * @param share   the share of payouts the company keeps, in percent
     * @param entries the latest movements, newest first
     */
    public record AdminTreasury(String company, double balance, int share, List<TreasuryEntry> entries) {}

    /**
     * A company level as an admin edits it. The name is the MiniMessage text as written in {@code company.yml}.
     *
     * @param level              the level number, from one
     * @param name               the name
     * @param orderCount         orders a company must have done
     * @param memberCount        members a company must have
     * @param earnedMoney        money a company must have earned
     * @param deliveredItems     items a company must have delivered
     * @param maxMembers         the most members
     * @param maxOrders          the most orders at once
     * @param maxOrderSize       the most items an accepted order may have, 0 for no limit
     * @param deliveryHoursBonus extra hours to deliver
     * @param taxPercent         the tax on payouts, none for the global tax
     * @param maxAlerts          the most order alerts, none for the global limit
     */
    public record AdminLevel(
            int level,
            String name,
            int orderCount,
            int memberCount,
            double earnedMoney,
            int deliveredItems,
            int maxMembers,
            int maxOrders,
            int maxOrderSize,
            int deliveryHoursBonus,
            @Nullable Double taxPercent,
            @Nullable Integer maxAlerts) {}

    /**
     * The company levels for an admin.
     *
     * @param editable whether this node may change them, which only the primary does while it can read
     *                 {@code company.yml}
     * @param blocked  why it may not: {@code secondary} or {@code unreadable}, none when it may
     * @param levels   the levels, lowest first
     */
    public record AdminLevels(boolean editable, @Nullable String blocked, List<AdminLevel> levels) {}

    /**
     * A board preset with its open orders.
     *
     * @param name  the preset's name, as signs and NPCs name it
     * @param label what a board of it shows as title
     * @param open  the open orders it shows
     */
    public record AdminBoardPreset(String name, String label, int open) {}

    /**
     * An NPC bound to a board.
     *
     * @param hook  the NPC plugin
     * @param npc   the NPC's key in that plugin
     * @param board the preset or materials, empty for every open order
     */
    public record AdminNpcBinding(String hook, String npc, String board) {}

    /**
     * The NPCs bound to boards on a node.
     *
     * @param hooks    the NPC plugins loaded on the node
     * @param bindings the bound NPCs
     */
    public record AdminNpcBoards(List<String> hooks, List<AdminNpcBinding> bindings) {}

    /**
     * A node registered in the database.
     *
     * @param uid      its {@code nodeUid}
     * @param role     {@code PRIMARY} or {@code SECONDARY}
     * @param version  the plugin version it last ran
     * @param lastSeen when it last started or synced its configuration, none before this was recorded
     * @param bus      how it exchanges messages with the others, as the admin reads it
     * @param current  whether it is the node answering, which cannot be removed
     * @param behind   whether it uses an older revision of a shared settings file than the latest
     * @param restart  the changed settings it applies only after a restart, none when no restart is pending
     */
    public record AdminNode(
            UUID uid,
            String role,
            String version,
            @Nullable Instant lastSeen,
            String bus,
            boolean current,
            boolean behind,
            @Nullable String restart) {}

    /**
     * A shared settings file as the dashboard's settings editor shows it.
     *
     * @param file       the file, such as {@code config.yml}
     * @param revision   its latest revision, 0 before one was stored
     * @param node       the answering node, to which the next three and the sources of the secrets belong
     * @param applied    the revision the answering node uses
     * @param editedHere whether the answering node changed it without sharing the change
     * @param behind     the nodes that use an older revision
     * @param settings   its settings, in the order they are shown
     * @param editable   whether the admin may change it, which needs {@code companies.admin.reload}
     */
    public record AdminSettingsFile(
            String file,
            int revision,
            UUID node,
            int applied,
            boolean editedHere,
            List<UUID> behind,
            List<AdminSetting> settings,
            boolean editable) {}

    /**
     * One shared setting.
     *
     * @param section    the section it is shown in
     * @param key        its dotted key
     * @param type       how it is entered: {@code FLAG}, {@code WHOLE}, {@code DECIMAL}, {@code TEXT}, {@code CHOICE},
     *                   {@code CHOICES}, {@code TEXT_LIST}, {@code FLAG_MAP} or {@code SECRET}
     * @param choices    the values to choose from, empty unless a choice
     * @param restart    whether it applies only after a restart
     * @param value      its value in the latest revision, none for a secret
     * @param secret     whether a secret is set and where from, none unless a secret
     * @param overridden whether the answering node sets it for itself in its {@code node.yml}
     * @param min        the smallest number allowed, none unless a number
     * @param max        the largest number allowed, none when unbounded
     */
    public record AdminSetting(
            String section,
            String key,
            String type,
            List<String> choices,
            boolean restart,
            @Nullable JsonNode value,
            @Nullable AdminSecret secret,
            boolean overridden,
            @Nullable Double min,
            @Nullable Double max) {}

    /**
     * Whether a secret setting is set, never its value.
     *
     * @param set    whether it has a value
     * @param source {@code environment}, {@code file} or {@code none}
     */
    public record AdminSecret(boolean set, String source) {}

    /**
     * One revision of a shared settings file.
     *
     * @param revision its number
     * @param actor    who stored it, such as an admin's name or {@code startup}
     * @param node     the node it was stored on
     * @param changed  when it was stored
     */
    public record AdminSettingsChange(int revision, String actor, UUID node, Instant changed) {}

    /**
     * What one revision changed against the one before.
     *
     * @param file     the file
     * @param revision the revision
     * @param actor    who stored it
     * @param node     the node it was stored on
     * @param changed  when it was stored
     * @param first    whether it is the first revision, which holds the file as it was first stored
     * @param changes  the settings it changed, empty for the first revision
     */
    public record AdminSettingsRevision(
            String file,
            int revision,
            String actor,
            UUID node,
            Instant changed,
            boolean first,
            List<AdminSettingDiff> changes) {}

    /**
     * One setting a revision changed.
     *
     * @param key      the dotted key of the setting, or of the part edited in the file only, such as {@code level}
     * @param before   its value before, none for a secret, a setting edited in the file only or one that was missing
     * @param after    its value after, none as for {@code before}
     * @param secret   whether it is a secret, whose values are never shown
     * @param fileOnly whether it is edited in the file only, whose values are not shown here
     */
    public record AdminSettingDiff(
            String key, @Nullable JsonNode before, @Nullable JsonNode after, boolean secret, boolean fileOnly) {}

    /**
     * What saving settings or rolling a file back did.
     *
     * @param status   {@code saved}, {@code unchanged} (nothing differed from the latest revision), {@code invalid} or
     *                 {@code newer} (another revision was stored after the one the admin saw; nothing was stored)
     * @param revision the stored revision, the latest one when unchanged or newer, 0 when invalid
     * @param fields   why each refused value was refused, by dotted key, when invalid
     * @param values   the submitted settings as the newer revision holds them, without secrets, when newer
     * @param changed  the settings the newer revisions changed, when newer
     * @param restart  the saved settings that apply only after a restart, when saved
     * @param applied  whether the answering node uses the stored revision; not when a file changed there could not be
     *                 backed up
     */
    public record AdminSettingsSaved(
            String status,
            int revision,
            Map<String, String> fields,
            Map<String, JsonNode> values,
            List<String> changed,
            List<String> restart,
            boolean applied) {}
}
