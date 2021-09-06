package de.eldoria.companies.events.company;

import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;

public class CompanyLevelChangeEvent extends CompanyEvent<CompanyProfile> {
    private final CompanyLevel oldLevel;
    private final CompanyLevel newLevel;

    public CompanyLevelChangeEvent(CompanyProfile company, CompanyLevel oldLevel, CompanyLevel newLevel) {
        super(company);
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    /**
     * The old level before the change
     *
     * @return company level
     */
    public CompanyLevel oldLevel() {
        return oldLevel;
    }

    /**
     * The new level after the change
     *
     * @return company level
     */
    public CompanyLevel newLevel() {
        return newLevel;
    }
}
