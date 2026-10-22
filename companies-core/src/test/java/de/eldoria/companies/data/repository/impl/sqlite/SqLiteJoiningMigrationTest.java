/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.core.updater.SqlVersion;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.DetailsResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Upgrades a database with companies and members from the previous version and checks the profile columns and the
 * new invite and application tables.
 */
class SqLiteJoiningMigrationTest {
    private final UUID member = UUID.randomUUID();

    @TempDir
    Path directory;

    private SqLiteCompanyData companyData;
    private int miners;
    private int farmers;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory, new SqlVersion(1, 4));
        miners = insertCompany("Miners");
        farmers = insertCompany("Farmers");
        query("INSERT INTO company_member(id, member_uuid, permission) VALUES (?, ?, 1)")
                .single(call().bind(miners).bind(member, UUID_BYTES))
                .insert();

        SqLiteTestDatabase.create(directory);
        companyData = new SqLiteCompanyData(Executors.newSingleThreadExecutor());
    }

    private static int insertCompany(String name) {
        return query("INSERT INTO companies(name) VALUES (?) RETURNING id")
                .single(call().bind(name))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    @Test
    void existingCompaniesStayInviteOnlyWithoutDetails() {
        for (var id : new int[] {miners, farmers}) {
            var company = companyData.retrieveCompanyById(id).join().orElseThrow();
            Assertions.assertEquals(CompanyDetails.NONE, company.details());
        }
    }

    @Test
    void membersAndFoundingDateSurvive() {
        var profile = companyData
                .retrievePlayerCompanyProfile(SqLiteTestDatabase.player(member))
                .join()
                .orElseThrow();

        Assertions.assertEquals(miners, profile.id());
        Assertions.assertTrue(profile.isMember(member));
        Assertions.assertNotNull(profile.founded());
    }

    @Test
    void tagsAreUniqueAfterTheUpgrade() {
        var first = companyData.retrieveCompanyById(miners).join().orElseThrow();
        var second = companyData.retrieveCompanyById(farmers).join().orElseThrow();

        Assertions.assertEquals(
                DetailsResult.SAVED,
                companyData
                        .submitDetails(first, CompanyDetails.NONE.withTag("ORE"))
                        .join());
        Assertions.assertEquals(
                DetailsResult.TAG_TAKEN,
                companyData
                        .submitDetails(second, CompanyDetails.NONE.withTag("ore"))
                        .join());
    }

    @Test
    void inviteAndApplicationTablesExist() {
        Assertions.assertEquals(
                2,
                SqLiteTestDatabase.count(
                        "sqlite_master", "type = 'table' AND name IN ('company_invites', 'company_applications')"));
        Assertions.assertEquals(0, SqLiteTestDatabase.count("company_invites", "TRUE"));
        Assertions.assertEquals(0, SqLiteTestDatabase.count("company_applications", "TRUE"));
    }

    @Test
    void newCompaniesUseTheGivenJoinMode() {
        var id = companyData
                .submitCompanyCreation("Smiths", JoinMode.APPLICATIONS)
                .join();

        Assertions.assertEquals(
                JoinMode.APPLICATIONS,
                companyData.retrieveCompanyById(id).join().orElseThrow().joinMode());
    }
}
