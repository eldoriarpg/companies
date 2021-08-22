package de.eldoria.companies.data.wrapper.company;

import java.time.LocalDateTime;

public class CompanyStats {
    private final int id;
    private final String name;
    private final LocalDateTime founded;
    private final int memberCount;
    private final int orderCount;
    private final double price;
    private final int amount;

    public CompanyStats(int id, String name, LocalDateTime founded, int memberCount, int orderCount, double price, int amount) {
        this.id = id;
        this.name = name;
        this.founded = founded;
        this.memberCount = memberCount;
        this.orderCount = orderCount;
        this.price = price;
        this.amount = amount;
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

    public int amount() {
        return amount;
    }
}
