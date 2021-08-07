package de.eldoria.companies.data.wrapper.company;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SimpleCompany {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy");
    int id;
    String name;
    LocalDateTime founded;

    public SimpleCompany(int id, String name, LocalDateTime founded) {
        this.id = id;
        this.name = name;
        this.founded = founded;
    }

    public static SimpleCompany forId(int id) {
        return new SimpleCompany(id, "", null);
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

    public String foundedString() {
        return founded.format(FORMATTER);
    }

    public CompanyProfile toCompanyProfile(List<CompanyMember> members) {
        return new CompanyProfile(id, name, founded, members);
    }
}
