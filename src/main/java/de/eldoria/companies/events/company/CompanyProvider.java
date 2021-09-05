package de.eldoria.companies.events.company;

import de.eldoria.companies.data.wrapper.company.SimpleCompany;

public interface CompanyProvider<Company extends SimpleCompany> {
    Company company();
}
