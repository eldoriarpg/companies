package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.data.wrapper.order.FullOrder;

import java.time.LocalDateTime;
import java.util.List;

public class CompanyProfile extends SimpleCompany{
    List<CompanyMember> members;

    public CompanyProfile(int id, String name, LocalDateTime founded, List<CompanyMember> members) {
        super(id, name, founded);
        this.members = members;
    }

    public Company toCompany(List<FullOrder> orders){
        return new Company(id, name, founded, members, orders);
    }
}
