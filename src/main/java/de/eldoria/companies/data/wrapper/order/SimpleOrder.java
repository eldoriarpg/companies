package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.orders.OrderState;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DurationFormatUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public class SimpleOrder {
    private final int id;
    private final UUID owner;
    private final LocalDateTime created;
    private final int company;
    private final LocalDateTime claimed;
    private final OrderState state;
    private String name;

    public SimpleOrder(UUID owner, String name) {
        this.id = -1;
        this.owner = owner;
        this.name = StringUtils.left(name, 32);
        this.created = null;
        this.company = -1;
        this.claimed = null;
        this.state = null;
    }

    public SimpleOrder(int id, UUID owner, String name, LocalDateTime created, int company, LocalDateTime claimed, OrderState state) {
        this.id = id;
        this.owner = owner;
        this.name = StringUtils.left(name, 32);
        this.created = created;
        this.company = company;
        this.claimed = claimed;
        this.state = state;
    }

    public FullOrder toFullOrder(List<OrderContent> contents) {
        return new FullOrder(id, owner, name, created, company, claimed, state, contents);
    }

    public void name(String name) {
        this.name = name;
    }

    public int id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String name() {
        return StringUtils.left(name, 32);
    }

    public String fullName() {
        return String.format("%s - %s", id, name);
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

    public String runningOutTime(Configuration configuration) {
        var duration = Duration.between(LocalDateTime.now(), claimed().plus(configuration.companySettings().deliveryHours(), ChronoUnit.HOURS));
        if (duration.toDays() > 0L) {
            return DurationFormatUtils.formatDuration(duration.toMillis(), "dd:HH:mm");
        }
        return DurationFormatUtils.formatDuration(duration.toMillis(), "HH:mm");
    }
}
