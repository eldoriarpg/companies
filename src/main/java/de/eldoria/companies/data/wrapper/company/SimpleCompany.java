package de.eldoria.companies.data.wrapper.company;

import java.time.LocalDateTime;
import java.util.List;

public class SimpleCompany {
    int id;
    String name;
    LocalDateTime founded;

    public SimpleCompany(int id, String name, LocalDateTime founded) {
        this.id = id;
        this.name = name;
        this.founded = founded;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public CompanyProfile toCompanyProfile(List<CompanyMember> members) {
        return new CompanyProfile(id, name, founded, members);
    }
}
