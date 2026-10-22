/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.company.JoinMode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class SimpleCompany implements ISimpleCompany {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy");
    private final int id;
    private final String name;
    private final LocalDateTime founded;
    private final int level;
    private final CompanyDetails details;

    public SimpleCompany(int id, String name, LocalDateTime founded, int level, CompanyDetails details) {
        this.id = id;
        this.name = name;
        this.founded = founded;
        this.level = level;
        this.details = details;
    }

    public static SimpleCompany forId(int id) {
        return new SimpleCompany(id, "", null, 1, CompanyDetails.NONE);
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public LocalDateTime founded() {
        return founded;
    }

    @Override
    public String foundedString() {
        return founded.format(FORMATTER);
    }

    @Override
    public int level() {
        return level;
    }

    /**
     * The editable parts of the profile.
     *
     * @return details
     */
    public CompanyDetails details() {
        return details;
    }

    /**
     * The free text the company shows on its profile.
     *
     * @return description, or empty when none is set
     */
    public Optional<String> description() {
        return Optional.ofNullable(details.description());
    }

    /**
     * The short tag of the company.
     *
     * @return upper case tag, or empty when none is set
     */
    public Optional<String> tag() {
        return Optional.ofNullable(details.tag());
    }

    /**
     * The colour of the company's tag and name.
     *
     * @return colour name or {@code #rrggbb} value, or empty when none is set
     */
    public Optional<String> color() {
        return Optional.ofNullable(details.color());
    }

    /**
     * How players become members of the company.
     *
     * @return join mode
     */
    public JoinMode joinMode() {
        return details.joinMode();
    }

    public CompanyProfile toCompanyProfile(List<CompanyMember> members) {
        return new CompanyProfile(id, name, founded, level, details, members);
    }
}
