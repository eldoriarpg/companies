package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.data.wrapper.order.Order;

import java.time.LocalDateTime;
import java.util.List;

public class Company extends CompanyProfile {
    List<Order> orders;

    public Company(int id, String name, LocalDateTime founded, List<CompanyMember> members, List<Order> orders) {
        super(id, name, founded, members);
        this.orders = orders;
    }
}
