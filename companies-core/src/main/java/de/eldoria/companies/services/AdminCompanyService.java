/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;

import java.util.Optional;
import java.util.UUID;

/**
 * What an admin does to any company, as `/companyadmin rename` and `/companyadmin transferowner` do: rename it free of
 * charge, and make another member its owner. The commands and the dashboard's admin tools share it; the callers write
 * the admin log. The methods block on the database.
 */
public final class AdminCompanyService {
    private final ACompanyData companies;
    private final CompanyChanges changes;

    /**
     * @param companies company repository
     * @param changes   tells the plugin about renames and owner changes
     */
    public AdminCompanyService(ACompanyData companies, CompanyChanges changes) {
        this.companies = companies;
        this.changes = changes;
    }

    /**
     * A company by its name.
     *
     * @param name the name
     * @return the company with its members, empty when no company has the name
     */
    public Optional<CompanyProfile> profile(String name) {
        var company = companies.retrieveCompanyByName(name).asFuture().join();
        if (company.isEmpty()) return Optional.empty();
        var profile = companies.retrieveCompanyProfile(company.get()).join();
        return profile == null ? Optional.empty() : profile;
    }

    /**
     * Renames a company free of charge. The new name must not be another company's and is held to the length players
     * may choose.
     *
     * @param company the company
     * @param name    the new name
     * @param admin   the admin renaming it
     * @return the company's former name, or why it was not renamed
     */
    public ActionResult<String> rename(SimpleCompany company, String name, UUID admin) {
        if (name.isBlank() || name.length() >= CompanyService.MAX_NAME_LENGTH) return CompanyService.invalidLength();
        var other = companies.retrieveCompanyByName(name).join();
        if (other == null) return ActionResult.refusedChat("error.saveFailed");
        if (other.isPresent() && other.get().id() != company.id())
            return ActionResult.refusedChat("error.companyNameUsed");
        if (!Boolean.TRUE.equals(companies.updateCompanyName(company, name).join())) {
            return ActionResult.refusedChat("error.companyNameUsed");
        }
        changes.renamed(company.id(), company.name(), admin, true);
        return ActionResult.done(company.name());
    }

    /**
     * Makes a member the owner of a company. The former owner stays a member without owner rights.
     *
     * @param profile the company with its members
     * @param target  the member who becomes the owner
     * @return the former and the new owner, or why the company kept its owner
     */
    public ActionResult<Transfer> transferOwner(CompanyProfile profile, UUID target) {
        var owner = profile.owner();
        var newOwner = profile.member(target);
        if (newOwner.isEmpty()) return ActionResult.refusedChat("error.noCompanyMember");
        owner.isOwner(false);
        newOwner.get().isOwner(true);
        companies.submitMemberUpdate(newOwner.get()).join();
        companies.submitMemberUpdate(owner).join();
        if (!owner.uuid().equals(newOwner.get().uuid())) {
            changes.ownerTransferred(profile.id(), owner.uuid(), newOwner.get().uuid(), true);
        }
        return ActionResult.done(new Transfer(owner, newOwner.get()));
    }

    /**
     * A company that changed hands.
     *
     * @param from the former owner
     * @param to   the new owner
     */
    public record Transfer(CompanyMember from, CompanyMember to) {}
}
