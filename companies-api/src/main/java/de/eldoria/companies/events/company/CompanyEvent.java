/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.events.components.CompanyProvider;
import org.bukkit.event.Event;

/**
 * A company event.
 * <p>
 * This event is for internal use and should not be subscribed directly
 *
 * @param <Company> type of company implementation
 */
public abstract class CompanyEvent<Company extends ISimpleCompany> extends Event implements CompanyProvider<Company> {
    private final Company company;

    public CompanyEvent(Company company) {
        this.company = company;
    }

    public CompanyEvent(Company company, boolean async) {
        super(async);
        this.company = company;
    }

    /**
     * The involved company
     *
     * @return company
     */
    @Override
    public Company company() {
        return company;
    }

}
