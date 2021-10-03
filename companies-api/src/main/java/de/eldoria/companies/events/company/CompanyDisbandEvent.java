package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;

/**
 * Fired when a company gets disbaneded by its owner.
 */
public class CompanyDisbandEvent extends CompanyEvent<ICompanyProfile> {
    public CompanyDisbandEvent(ICompanyProfile company) {
        super(company);
    }
}
