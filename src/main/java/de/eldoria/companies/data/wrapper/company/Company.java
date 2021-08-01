package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.data.wrapper.order.FullOrder;

import java.time.LocalDateTime;
import java.util.List;

public class Company extends CompanyProfile {
    List<FullOrder> orders;

    public Company(int id, String name, LocalDateTime founded, List<CompanyMember> members, List<FullOrder> orders) {
        super(id, name, founded, members);
        this.orders = orders;
    }
}
