/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.level.ICompanyLevel;

/**
 * The level of a company decreased.
 * <p>
 * Fired off the main thread. Uses the handler list of {@link CompanyLevelChangeEvent}.
 */
public class CompanyLevelDownEvent extends CompanyLevelChangeEvent {
    /**
     * Creates a new event.
     *
     * @param company  the company
     * @param oldLevel level before the change
     * @param newLevel level after the change
     */
    public CompanyLevelDownEvent(ICompanyProfile company, ICompanyLevel oldLevel, ICompanyLevel newLevel) {
        super(company, oldLevel, newLevel);
    }
}
