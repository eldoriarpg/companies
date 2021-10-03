package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.components.company.ICompanyStats;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.localization.MessageComposer;

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

    public CompanyStats(int id, String name, LocalDateTime founded, int memberCount, int orderCount, double price, int deliveredItems) {
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

    public String asComponent() {
        return MessageComposer.create()
                .text("<%s>", Colors.HEADING).text(name).newLine()
                .text("<%s>", Colors.NAME).localeCode("words.founded").text(": <%s>%s ", Colors.VALUE, FORMATTER.format(founded)).newLine()
                .text("<%s>", Colors.NAME).localeCode("words.member").text(": <%s>%s", Colors.VALUE, memberCount).newLine()
                .text("<%s>", Colors.NAME).localeCode("words.orders").text(": <%s>%s", Colors.VALUE, orderCount).newLine()
                .text("<%s>", Colors.NAME).localeCode("level.earnedMoney").text(": <%s>%s", Colors.VALUE, price).newLine()
                .text("<%s>", Colors.NAME).localeCode("level.deliveredItems").text(": <%s>%s", Colors.VALUE, deliveredItems)
                .build();
    }
}
