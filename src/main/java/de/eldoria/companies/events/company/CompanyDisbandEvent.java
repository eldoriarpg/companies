package de.eldoria.companies.events.company;

import de.eldoria.companies.data.wrapper.company.CompanyProfile;

/**
 * Fired when a company gets disbaneded by its owner.
 */
public class CompanyDisbandEvent extends CompanyEvent<CompanyProfile> {
    public CompanyDisbandEvent(CompanyProfile company) {
        super(company);
    }
}
