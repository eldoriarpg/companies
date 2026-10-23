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
 * Base of the events about a company. Listen to its subclasses; this class has no handler list.
 * <p>
 * Company events fire only on the server where the action happened.
 *
 * @param <Company> type of company
 */
public abstract class CompanyEvent<Company extends ISimpleCompany> extends Event implements CompanyProvider<Company> {
    private final Company company;

    /**
     * Creates a synchronous event.
     *
     * @param company the company
     */
    public CompanyEvent(Company company) {
        this.company = company;
    }

    /**
     * Creates an event.
     *
     * @param company the company
     * @param async   whether the event is fired off the main thread
     */
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
