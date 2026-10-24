/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import de.eldoria.companies.components.order.IFullOrder;

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
                .map(content -> new Line(content.amount(), DiscordText.itemName(content.materialIdentifier())))
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
}
