package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.level.ICompanyLevel;

/**
 * A company level decreases.
 */
public class CompanyLevelDownEvent extends CompanyLevelChangeEvent {
    public CompanyLevelDownEvent(ICompanyProfile company, ICompanyLevel oldLevel, ICompanyLevel newLevel) {
        super(company, oldLevel, newLevel);
    }
}
