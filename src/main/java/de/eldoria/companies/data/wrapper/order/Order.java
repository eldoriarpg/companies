package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.oders.OrderState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class Order {
    long id;
    UUID owner;
    String name;
    LocalDateTime created;
    int company;
    LocalDateTime claimed;
    OrderState state;
    List<OrderContent> contents;

    public Order(long id, UUID owner, String name, LocalDateTime created, int company, LocalDateTime claimed, OrderState state) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.created = created;
        this.company = company;
        this.claimed = claimed;
        this.state = state;
    }

    public void contents(List<OrderContent> contents) {
        this.contents = contents;
    }

    public long id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public LocalDateTime created() {
        return created;
    }

    public int company() {
        return company;
    }

    public LocalDateTime claimed() {
        return claimed;
    }

    public OrderState state() {
        return state;
    }

    public List<OrderContent> contents() {
        return contents;
    }
}
