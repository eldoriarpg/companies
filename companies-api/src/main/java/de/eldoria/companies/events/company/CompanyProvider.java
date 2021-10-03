package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ISimpleCompany;

public interface CompanyProvider<Company extends ISimpleCompany> {
    Company company();
}
