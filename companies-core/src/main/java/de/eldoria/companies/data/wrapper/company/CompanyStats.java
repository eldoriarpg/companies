/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.components.company.ICompanyStats;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.milkbowl.vault.economy.Economy;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CompanyStats implements ICompanyStats {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy");
    private final int id;
    private final String name;
    private final LocalDateTime founded;
    private final int memberCount;
    private final int orderCount;
    private final double price;
    private final int deliveredItems;

    public CompanyStats(
            int id,
            String name,
            LocalDateTime founded,
            int memberCount,
            int orderCount,
            double price,
            int deliveredItems) {
        this.id = id;
        this.name = name;
        this.founded = founded;
        this.memberCount = memberCount;
        this.orderCount = orderCount;
        this.price = price;
        this.deliveredItems = deliveredItems;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public LocalDateTime founded() {
        return founded;
    }

    @Override
    public int memberCount() {
        return memberCount;
    }

    @Override
    public int orderCount() {
        return orderCount;
    }

    @Override
    public double price() {
        return price;
    }

    @Override
    public int deliveredItems() {
        return deliveredItems;
    }

    public CompanyRank toRank(int rank) {
        return new CompanyRank(rank, id, name, founded, memberCount, orderCount, price, deliveredItems);
    }

    /**
     * The statistics as hover text.
     *
     * @param economy economy formatting the earned money
     * @return MiniMessage text
     */
    public String asComponent(Economy economy) {
        return MessageComposer.create()
                .text("<heading>")
                .text(Texts.escape(name))
                .newLine()
                .text("<name>")
                .localeCode("words.founded")
                .text(": <value>%s ", FORMATTER.format(founded))
                .newLine()
                .text("<name>")
                .localeCode("words.member")
                .text(": <value>%s", memberCount)
                .newLine()
                .text("<name>")
                .localeCode("words.orders")
                .text(": <value>%s", orderCount)
                .newLine()
                .text("<name>")
                .localeCode("level.earnedMoney")
                .text(": <value>%s", economy.format(price))
                .newLine()
                .text("<name>")
                .localeCode("level.deliveredItems")
                .text(": <value>%s", deliveredItems)
                .build();
    }
}
