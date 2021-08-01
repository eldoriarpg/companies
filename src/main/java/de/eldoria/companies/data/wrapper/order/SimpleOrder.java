package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.orders.OrderState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class SimpleOrder {
    private final long id;
    private final UUID owner;
    private final String name;
    private final LocalDateTime created;
    private final int company;
    private final LocalDateTime claimed;
    private final OrderState state;

    public SimpleOrder(UUID owner, String name) {
        this.id = -1L;
        this.owner = owner;
        this.name = name;
        this.created = null;
        this.company = -1;
        this.claimed = null;
        this.state = null;
    }

    public SimpleOrder(long id, UUID owner, String name, LocalDateTime created, int company, LocalDateTime claimed, OrderState state) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.created = created;
        this.company = company;
        this.claimed = claimed;
        this.state = state;
    }

    public FullOrder toFullOrder(List<OrderContent> contents) {
        return new FullOrder(id, owner, name, created, company, claimed, state, contents);
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
}
