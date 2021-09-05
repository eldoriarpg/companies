package de.eldoria.companies.events.company;

import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;

/**
 * Fired when a company looses a level.
 */
public class CompanyLevelDownEvent extends CompanyLevelChangeEvent {
    public CompanyLevelDownEvent(CompanyProfile company, CompanyLevel oldLevel, CompanyLevel newLevel) {
        super(company, oldLevel, newLevel);
    }
}
