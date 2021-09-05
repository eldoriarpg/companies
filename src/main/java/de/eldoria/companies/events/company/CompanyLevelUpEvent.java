package de.eldoria.companies.events.company;

import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;

/**
 * Fired when a company gets disbaneded by its owner.
 */
public class CompanyLevelUpEvent extends CompanyLevelChangeEvent {
    public CompanyLevelUpEvent(CompanyProfile company, CompanyLevel oldLevel, CompanyLevel newLevel) {
        super(company, oldLevel, newLevel);
    }
}
