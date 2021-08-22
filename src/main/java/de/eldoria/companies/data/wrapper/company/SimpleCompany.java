package de.eldoria.companies.data.wrapper.company;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SimpleCompany {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy");
    private final int id;
    private final String name;
    private final LocalDateTime founded;
    private final int level;

    public SimpleCompany(int id, String name, LocalDateTime founded, int level) {
        this.id = id;
        this.name = name;
        this.founded = founded;
        this.level = level;
    }

    public static SimpleCompany forId(int id) {
        return new SimpleCompany(id, "", null, 1);
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

    public int level() {
        return level;
    }

    public CompanyProfile toCompanyProfile(List<CompanyMember> members) {
        return new CompanyProfile(id, name, founded, level, members);
    }
}
