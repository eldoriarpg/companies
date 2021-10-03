package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.level.ICompanyLevel;

/**
 * Fired when a company gets disbaneded by its owner.
 */
public class CompanyLevelUpEvent extends CompanyLevelChangeEvent {
    public CompanyLevelUpEvent(ICompanyProfile company, ICompanyLevel oldLevel, ICompanyLevel newLevel) {
        super(company, oldLevel, newLevel);
    }
}
