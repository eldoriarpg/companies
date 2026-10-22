/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.wrapper.company.CompanyMember;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;

class SqLiteCompanyDataTest {
    @TempDir
    Path directory;
    private SqLiteCompanyData companyData;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        companyData = new SqLiteCompanyData(Executors.newSingleThreadExecutor());
    }

    private int createCompany(String name) {
        var id = companyData.submitCompanyCreation(name).join();
        companyData.submitMemberUpdate(CompanyMember.of(id, UUID.randomUUID(), 0)).join();
        return id;
    }

    @Test
    void companyIsFoundByNameIgnoringCase() {
        var id = createCompany("Miners");

        Assertions.assertEquals(id, companyData.retrieveCompanyByName("miners").join().orElseThrow().id());
        Assertions.assertTrue(companyData.retrieveCompanyByName("Min%").join().isEmpty());
    }

    @Test
    void purgedCompanyIsGone() {
        var id = createCompany("Miners");
        var company = companyData.retrieveCompanyById(id).join().orElseThrow();

        Assertions.assertTrue(companyData.submitCompanyPurge(company).join());

        Assertions.assertTrue(companyData.retrieveCompanyById(id).join().isEmpty());
        Assertions.assertTrue(companyData.retrieveCompanyByName("Miners").join().isEmpty());
        Assertions.assertEquals(0, SqLiteTestDatabase.count("company_member", "id = " + id));
    }

    @Test
    void failedOrdersAreCounted() {
        var id = createCompany("Miners");
        var company = companyData.retrieveCompanyById(id).join().orElseThrow();

        companyData.upcountFailedOrders(company, 2);
        companyData.upcountFailedOrders(company, 3);

        Assertions.assertEquals(1, SqLiteTestDatabase.count("company_stats", "id = " + id + " AND failed_orders = 5"));
    }
}
