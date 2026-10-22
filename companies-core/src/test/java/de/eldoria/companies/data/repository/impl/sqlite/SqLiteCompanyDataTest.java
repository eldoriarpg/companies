/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.DetailsResult;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
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
        var id = companyData.submitCompanyCreation(name, JoinMode.INVITE_ONLY).join();
        companyData
                .submitMemberUpdate(CompanyMember.of(id, UUID.randomUUID(), 0))
                .join();
        return id;
    }

    @Test
    void companyIsFoundByNameIgnoringCase() {
        var id = createCompany("Miners");

        Assertions.assertEquals(
                id,
                companyData.retrieveCompanyByName("miners").join().orElseThrow().id());
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

    private boolean isOwner(int company, UUID member) {
        return companyData
                .retrieveCompanyProfile(
                        companyData.retrieveCompanyById(company).join().orElseThrow())
                .join()
                .orElseThrow()
                .members()
                .stream()
                .filter(m -> m.uuid().equals(member))
                .findFirst()
                .orElseThrow()
                .isOwner();
    }

    @Test
    void ownershipMovesToTheTarget() {
        var owner = UUID.randomUUID();
        var member = UUID.randomUUID();
        var id = companyData
                .submitCompanyCreation("Miners", JoinMode.INVITE_ONLY)
                .join();
        companyData
                .submitMemberUpdate(CompanyMember.of(id, owner, CompanyPermission.OWNER.mask()))
                .join();
        companyData.submitMemberUpdate(CompanyMember.of(id, member, 0)).join();
        var company = companyData.retrieveCompanyById(id).join().orElseThrow();

        Assertions.assertTrue(
                companyData.submitOwnerTransfer(company, owner, member).join());
        Assertions.assertFalse(isOwner(id, owner));
        Assertions.assertTrue(isOwner(id, member));
        Assertions.assertFalse(
                companyData.submitOwnerTransfer(company, owner, member).join());
    }

    @Test
    void ownershipStaysWhenTheTargetIsNoMember() {
        var owner = UUID.randomUUID();
        var id = companyData
                .submitCompanyCreation("Miners", JoinMode.INVITE_ONLY)
                .join();
        companyData
                .submitMemberUpdate(CompanyMember.of(id, owner, CompanyPermission.OWNER.mask()))
                .join();
        var otherCompany = createCompany("Farmers");
        var outsider = UUID.randomUUID();
        companyData
                .submitMemberUpdate(CompanyMember.of(otherCompany, outsider, 0))
                .join();
        var company = companyData.retrieveCompanyById(id).join().orElseThrow();

        Assertions.assertFalse(
                companyData.submitOwnerTransfer(company, owner, outsider).join());
        Assertions.assertTrue(isOwner(id, owner));
        Assertions.assertFalse(isOwner(otherCompany, outsider));
    }

    private SimpleCompany company(int id) {
        return companyData.retrieveCompanyById(id).join().orElseThrow();
    }

    @Test
    void newCompanyHasNoDetailsAndTheGivenJoinMode() {
        var id = companyData.submitCompanyCreation("Miners", JoinMode.OPEN).join();

        var company = company(id);
        Assertions.assertEquals(JoinMode.OPEN, company.joinMode());
        Assertions.assertTrue(company.description().isEmpty());
        Assertions.assertTrue(company.tag().isEmpty());
        Assertions.assertTrue(company.color().isEmpty());
    }

    @Test
    void detailsAreSavedAndReadByEverySelect() {
        var id = createCompany("Miners");
        var details = new CompanyDetails("We dig.", "mine", "gold", JoinMode.APPLICATIONS);

        Assertions.assertEquals(
                DetailsResult.SAVED,
                companyData.submitDetails(company(id), details).join());

        var expected = details.withTag("MINE");
        Assertions.assertEquals(expected, company(id).details());
        Assertions.assertEquals(
                expected,
                companyData.retrieveCompanyByName("miners").join().orElseThrow().details());
        Assertions.assertEquals(
                expected,
                companyData.getCompanies().stream()
                        .filter(c -> c.id() == id)
                        .findFirst()
                        .orElseThrow()
                        .details());
        var profile = companyData.retrieveCompanyProfile(company(id)).join().orElseThrow();
        Assertions.assertEquals(expected, profile.details());
        Assertions.assertEquals(JoinMode.APPLICATIONS, profile.joinMode());
    }

    @Test
    void companyOfAPlayerCarriesItsDetails() {
        var id = companyData.submitCompanyCreation("Miners", JoinMode.OPEN).join();
        var member = UUID.randomUUID();
        companyData.submitMemberUpdate(CompanyMember.of(id, member, 0)).join();

        var company = companyData
                .retrievePlayerCompany(SqLiteTestDatabase.player(member))
                .join()
                .orElseThrow();

        Assertions.assertEquals(id, company.id());
        Assertions.assertEquals(JoinMode.OPEN, ((SimpleCompany) company).joinMode());
    }

    @Test
    void companyIsFoundByTagIgnoringCase() {
        var id = createCompany("Miners");
        companyData
                .submitDetails(company(id), CompanyDetails.NONE.withTag("MINE"))
                .join();

        Assertions.assertEquals(
                id,
                companyData.retrieveCompanyByTag("mine").join().orElseThrow().id());
        Assertions.assertTrue(companyData.retrieveCompanyByTag("MIN").join().isEmpty());
    }

    @Test
    void tagOfAnotherCompanyIsTakenInAnyCase() {
        var miners = createCompany("Miners");
        var farmers = createCompany("Farmers");
        companyData
                .submitDetails(company(miners), CompanyDetails.NONE.withTag("IRON"))
                .join();

        var result = companyData
                .submitDetails(company(farmers), new CompanyDetails("Crops.", "iron", "green", JoinMode.OPEN))
                .join();

        Assertions.assertEquals(DetailsResult.TAG_TAKEN, result);
        Assertions.assertEquals(CompanyDetails.NONE, company(farmers).details());
        Assertions.assertEquals("IRON", company(miners).tag().orElseThrow());
    }

    @Test
    void companyMayKeepItsOwnTag() {
        var id = createCompany("Miners");
        companyData
                .submitDetails(company(id), CompanyDetails.NONE.withTag("IRON"))
                .join();

        var result = companyData
                .submitDetails(company(id), company(id).details().withColor("red"))
                .join();

        Assertions.assertEquals(DetailsResult.SAVED, result);
        Assertions.assertEquals("red", company(id).color().orElseThrow());
    }

    @Test
    void clearedTagsDoNotBlockEachOther() {
        var miners = createCompany("Miners");
        var farmers = createCompany("Farmers");
        companyData
                .submitDetails(company(miners), CompanyDetails.NONE.withTag("IRON"))
                .join();

        Assertions.assertEquals(
                DetailsResult.SAVED,
                companyData.submitDetails(company(miners), CompanyDetails.NONE).join());
        Assertions.assertEquals(
                DetailsResult.SAVED,
                companyData
                        .submitDetails(company(farmers), CompanyDetails.NONE.withDescription("Crops."))
                        .join());
        Assertions.assertTrue(company(miners).tag().isEmpty());
        Assertions.assertEquals(
                DetailsResult.SAVED,
                companyData
                        .submitDetails(company(farmers), CompanyDetails.NONE.withTag("IRON"))
                        .join());
    }

    @Test
    void detailsOfAMissingCompanyAreNotSaved() {
        Assertions.assertEquals(
                DetailsResult.UNKNOWN_COMPANY,
                companyData
                        .submitDetails(SimpleCompany.forId(99), CompanyDetails.NONE.withTag("GONE"))
                        .join());
    }

    @Test
    void purgeRemovesInvitesAndApplications() {
        var id = createCompany("Miners");
        var player = UUID.randomUUID();
        query("INSERT INTO company_invites(company_id, target_uuid, inviter_uuid, created, expires)"
                        + " VALUES (?, ?, ?, '2026-01-01 10:00:00', '2026-01-02 10:00:00')")
                .single(call().bind(id).bind(player, UUID_BYTES).bind(player, UUID_BYTES))
                .insert();
        query("INSERT INTO company_applications(company_id, applicant_uuid, message, created, expires)"
                        + " VALUES (?, ?, 'hi', '2026-01-01 10:00:00', '2026-01-02 10:00:00')")
                .single(call().bind(id).bind(player, UUID_BYTES))
                .insert();

        Assertions.assertTrue(companyData.submitCompanyPurge(company(id)).join());

        Assertions.assertEquals(0, SqLiteTestDatabase.count("company_invites", "company_id = " + id));
        Assertions.assertEquals(0, SqLiteTestDatabase.count("company_applications", "company_id = " + id));
    }
}
