/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.orders.criteria.CriteriaText;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Builds the embeds posted to the webhook.
 * <p>
 * Order embeds show what is ordered and the price only. They never name the owner or the company, and no fee, tax or
 * payout. Text written by players is escaped, so it is shown as written and cannot format, link or ping.
 */
public final class DiscordEmbeds {
    static final int ORDER_CREATED_COLOR = 0x5865F2;
    static final int ORDER_COMPLETED_COLOR = 0x57F287;
    static final int LEVEL_UP_COLOR = 0xFEE75C;
    static final int OFFER_STARTED_COLOR = 0xEB459E;
    static final int GOAL_PROGRESS_COLOR = 0x57F287;
    static final int CONTEST_RESULT_COLOR = 0xFEE75C;
    static final int CONTEST_CANCELLED_COLOR = 0x99AAB5;
    static final int EVENT_STARTED_COLOR = 0xEB459E;
    static final int EVENT_ENDED_COLOR = 0x99AAB5;

    private final UnaryOperator<String> texts;
    private final InstantSource clock;

    /**
     * @param texts localizes a message key
     * @param clock time shown on the embeds
     */
    public DiscordEmbeds(UnaryOperator<String> texts, InstantSource clock) {
        this.texts = texts;
        this.clock = clock;
    }

    /**
     * The lines of an order with the plain names of their items.
     *
     * @param order the order
     * @return one line per requested item
     */
    public static List<Line> lines(IFullOrder order) {
        return order.contents().stream()
                .map(content -> new Line(
                        content.amount(),
                        content.criteria()
                                .map(CriteriaText::plain)
                                .orElseGet(() -> DiscordText.itemName(content.materialIdentifier()))))
                .toList();
    }

    /**
     * The embed for a new order.
     *
     * @param orderName name of the order
     * @param lines     requested items
     * @param price     formatted price
     * @return the embed
     */
    public Embed orderCreated(String orderName, List<Line> lines, String price) {
        return order("discord.orderCreated", ORDER_CREATED_COLOR, orderName, lines, Optional.of(price));
    }

    /**
     * The embed for a fully delivered order.
     *
     * @param orderName name of the order
     * @param lines     delivered items, empty when unknown
     * @param price     formatted price, empty when unknown
     * @return the embed
     */
    public Embed orderCompleted(String orderName, List<Line> lines, Optional<String> price) {
        return order("discord.orderCompleted", ORDER_COMPLETED_COLOR, orderName, lines, price);
    }

    /**
     * The embed for a company reaching a higher level.
     *
     * @param company name of the company
     * @param level   plain name of the new level
     * @return the embed
     */
    public Embed levelUp(String company, String level) {
        return new Embed(
                texts.apply("discord.levelUp"),
                bold(company),
                LEVEL_UP_COLOR,
                List.of(new Embed.Field(texts.apply("words.level"), DiscordText.escape(level), true)),
                clock.instant());
    }

    /**
     * The embed for an event offer that started.
     *
     * @param name   name of the offer
     * @param kind   localized kind of the offer
     * @param amount items asked for
     * @param item   plain name of what is asked for
     * @param price  formatted price per item
     * @param ends   when it ends
     * @return the embed
     */
    public Embed offerStarted(String name, String kind, int amount, String item, String price, Instant ends) {
        return new Embed(
                texts.apply("discord.offerStarted"),
                bold(name),
                OFFER_STARTED_COLOR,
                List.of(
                        new Embed.Field(texts.apply("words.kind"), DiscordText.escape(kind), true),
                        new Embed.Field(texts.apply("words.items"), amount + "× " + DiscordText.escape(item), true),
                        new Embed.Field(texts.apply("words.unitPrice"), DiscordText.escape(price), true),
                        new Embed.Field(texts.apply("words.ends"), "<t:" + ends.getEpochSecond() + ":R>", true)),
                clock.instant());
    }

    /**
     * The embed for a goal whose progress passed a threshold.
     *
     * @param name      name of the goal
     * @param threshold the threshold in percent, 100 for a reached goal
     * @param delivered items delivered
     * @param amount    items asked for
     * @return the embed
     */
    public Embed goalProgress(String name, int threshold, int delivered, int amount) {
        return new Embed(
                texts.apply(threshold >= 100 ? "discord.goalReached" : "discord.goalProgress"),
                bold(name),
                GOAL_PROGRESS_COLOR,
                List.of(
                        new Embed.Field(texts.apply("words.progress"), threshold + " %", true),
                        new Embed.Field(texts.apply("words.items"), delivered + " / " + amount, true)),
                clock.instant());
    }

    /**
     * The embed for the ranking of a closed contest.
     *
     * @param name   name of the contest
     * @param places the first places, best first
     * @return the embed
     */
    public Embed contestResult(String name, List<Place> places) {
        List<Embed.Field> fields = new ArrayList<>();
        if (!places.isEmpty()) {
            var text = new StringBuilder();
            for (var place : places) {
                var row = place.place() + ". " + DiscordText.escape(place.company()) + ": " + place.items() + "×"
                        + place.prize()
                                .map(prize -> " · " + DiscordText.escape(prize))
                                .orElse("");
                if (text.length() + row.length() + 1 > Embed.FIELD_VALUE_LIMIT) break;
                if (!text.isEmpty()) text.append('\n');
                text.append(row);
            }
            fields.add(new Embed.Field(texts.apply("words.ranking"), text.toString(), false));
        }
        return new Embed(
                texts.apply("discord.contestResult"), bold(name), CONTEST_RESULT_COLOR, fields, clock.instant());
    }

    /**
     * The embed for a cancelled contest.
     *
     * @param name name of the contest
     * @return the embed
     */
    public Embed contestCancelled(String name) {
        return new Embed(
                texts.apply("discord.contestCancelled"),
                bold(name),
                CONTEST_CANCELLED_COLOR,
                List.of(),
                clock.instant());
    }

    /**
     * The embed for a server event that started.
     *
     * @param name   name of the event
     * @param effect what it changes
     * @param scope  which orders it applies to
     * @param ends   when it ends
     * @param budget what is left of its budget, empty for none
     * @param offers the names of its offers
     * @return the embed
     */
    public Embed eventStarted(
            String name, String effect, String scope, Instant ends, Optional<String> budget, List<String> offers) {
        List<Embed.Field> fields = new ArrayList<>();
        fields.add(new Embed.Field(texts.apply("words.effect"), DiscordText.escape(effect), true));
        fields.add(new Embed.Field(texts.apply("words.scope"), DiscordText.escape(scope), true));
        fields.add(new Embed.Field(texts.apply("words.ends"), "<t:" + ends.getEpochSecond() + ":R>", true));
        budget.ifPresent(
                left -> fields.add(new Embed.Field(texts.apply("words.budget"), DiscordText.escape(left), true)));
        if (!offers.isEmpty()) {
            fields.add(
                    new Embed.Field(texts.apply("words.offers"), DiscordText.escape(String.join(", ", offers)), false));
        }
        return new Embed(texts.apply("discord.eventStarted"), bold(name), EVENT_STARTED_COLOR, fields, clock.instant());
    }

    /**
     * The embed for a server event that ended.
     *
     * @param titleKey the title: ended, ran out of money or cancelled
     * @param name     name of the event
     * @return the embed
     */
    public Embed eventEnded(String titleKey, String name) {
        return new Embed(texts.apply(titleKey), bold(name), EVENT_ENDED_COLOR, List.of(), clock.instant());
    }

    private Embed order(String titleKey, int color, String orderName, List<Line> lines, Optional<String> price) {
        List<Embed.Field> fields = new ArrayList<>();
        if (!lines.isEmpty()) fields.add(new Embed.Field(texts.apply("words.items"), items(lines), false));
        price.ifPresent(
                value -> fields.add(new Embed.Field(texts.apply("words.price"), DiscordText.escape(value), true)));
        return new Embed(texts.apply(titleKey), bold(orderName), color, fields, clock.instant());
    }

    /**
     * Lists the lines, one per row, leaving out whole rows that do not fit into a field.
     *
     * @param lines the lines
     * @return rows such as {@code 64× Iron Ingot}
     */
    static String items(List<Line> lines) {
        var text = new StringBuilder();
        for (var i = 0; i < lines.size(); i++) {
            var row = lines.get(i).amount() + "× "
                    + DiscordText.escape(lines.get(i).item());
            var more = "\n… +" + (lines.size() - i);
            if (text.length() + row.length() + 1 + more.length() > Embed.FIELD_VALUE_LIMIT) {
                return text.append(more).toString();
            }
            if (!text.isEmpty()) text.append('\n');
            text.append(row);
        }
        return text.toString();
    }

    private static String bold(String text) {
        return "**" + DiscordText.cut(DiscordText.escape(text), Embed.DESCRIPTION_LIMIT - 4) + "**";
    }

    /**
     * One requested item.
     *
     * @param amount requested amount
     * @param item   plain name of the item
     */
    public record Line(int amount, String item) {}

    /**
     * A place of a contest's ranking.
     *
     * @param place   the place, from 1
     * @param company name of the company
     * @param items   items it delivered
     * @param prize   the formatted prize it was paid, empty without one
     */
    public record Place(int place, String company, int items, Optional<String> prize) {}
}
