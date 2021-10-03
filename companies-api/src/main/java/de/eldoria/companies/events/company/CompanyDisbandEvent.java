package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;

/**
 * A company gets disbanded by its owner.
 */
public class CompanyDisbandEvent extends CompanyEvent<ICompanyProfile> {
    public CompanyDisbandEvent(ICompanyProfile company) {
        super(company);
    }
}
