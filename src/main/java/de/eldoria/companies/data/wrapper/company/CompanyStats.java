package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.localization.MessageComposer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CompanyStats {
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

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public LocalDateTime founded() {
        return founded;
    }

    public int memberCount() {
        return memberCount;
    }

    public int orderCount() {
        return orderCount;
    }

    public double price() {
        return price;
    }

    public int deliveredItems() {
        return deliveredItems;
    }

    public CompanyRank toRank(int rank) {
        return new CompanyRank(rank, id, name, founded, memberCount, orderCount, price, deliveredItems);
    }

    public String asComponent() {
        return MessageComposer.create()
                .text("<%s>", Colors.HEADING).text(name).newLine()
                .text("<%s>", Colors.NAME).localeCode("Founded").text(": <%s>%s ", Colors.VALUE, FORMATTER.format(founded)).newLine()
                .text("<%s>", Colors.NAME).localeCode("Member").text(": <%s>%s", Colors.VALUE, memberCount).newLine()
                .text("<%s>", Colors.NAME).localeCode("Orders").text(": <%s>%s", Colors.VALUE, orderCount).newLine()
                .text("<%s>", Colors.NAME).localeCode("Earned Money").text(": <%s>%s", Colors.VALUE, price).newLine()
                .text("<%s>", Colors.NAME).localeCode("Delivered Items").text(": <%s>%s", Colors.VALUE, deliveredItems)
                .build();
    }
}
