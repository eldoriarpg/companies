/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.mapper.wrapper.Row;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.data.wrapper.company.DetailsResult;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import de.eldoria.eldoutilities.threading.futures.FutureResult;
import org.bukkit.OfflinePlayer;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@SuppressWarnings("UnusedReturnValue")
public abstract class ACompanyData {
    private final ExecutorService executorService;

    public ACompanyData(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public BukkitFutureResult<Void> submitMemberUpdate(CompanyMember member) {
        return CompletableBukkitFuture.runAsync(() -> updateMember(member));
    }

    protected abstract void updateMember(CompanyMember member);

    public BukkitFutureResult<Optional<? extends ISimpleCompany>> retrievePlayerCompany(OfflinePlayer player) {
        return CompletableBukkitFuture.supplyAsync(() -> getPlayerCompany(player), executorService);
    }

    protected abstract Optional<SimpleCompany> getPlayerCompany(OfflinePlayer player);

    public BukkitFutureResult<Optional<CompanyProfile>> retrieveCompanyProfile(ISimpleCompany simpleCompany) {
        return CompletableBukkitFuture.supplyAsync(() -> toCompanyProfile((SimpleCompany) simpleCompany));
    }

    protected abstract Optional<CompanyProfile> toCompanyProfile(SimpleCompany simpleCompany);

    public BukkitFutureResult<Optional<CompanyProfile>> retrievePlayerCompanyProfile(OfflinePlayer player) {
        return CompletableBukkitFuture.supplyAsync(() -> getPlayerCompany(player)
                .map(company -> toCompanyProfile(company).get()));
    }

    public BukkitFutureResult<Optional<SimpleCompany>> retrieveCompanyByName(String name) {
        return CompletableBukkitFuture.supplyAsync(() -> getCompanyByName(name), executorService);
    }

    protected abstract Optional<SimpleCompany> getCompanyByName(String name);

    public BukkitFutureResult<Optional<SimpleCompany>> retrieveCompanyById(int id) {
        return CompletableBukkitFuture.supplyAsync(() -> getCompanyById(id), executorService);
    }

    protected abstract Optional<SimpleCompany> getCompanyById(int id);

    /**
     * Creates a company without members.
     *
     * @param name     name of the company
     * @param joinMode how players become members
     * @return id of the new company
     */
    public BukkitFutureResult<Integer> submitCompanyCreation(String name, JoinMode joinMode) {
        return CompletableBukkitFuture.supplyAsync(() -> createCompany(name, joinMode), executorService);
    }

    protected abstract Integer createCompany(String name, JoinMode joinMode);

    /**
     * Finds the company using a tag.
     *
     * @param tag tag in any case
     * @return the company, or empty when no company uses the tag
     */
    public BukkitFutureResult<Optional<SimpleCompany>> retrieveCompanyByTag(String tag) {
        return CompletableBukkitFuture.supplyAsync(() -> getCompanyByTag(tag), executorService);
    }

    protected abstract Optional<SimpleCompany> getCompanyByTag(String tag);

    /**
     * Saves description, tag, colour and join mode of a company in one step. The tag is stored in upper case; another
     * company with the same tag makes the whole change fail.
     *
     * @param company company to change
     * @param details new details
     * @return the outcome
     */
    public BukkitFutureResult<DetailsResult> submitDetails(ISimpleCompany company, CompanyDetails details) {
        return CompletableBukkitFuture.supplyAsync(() -> updateDetails(company, details), executorService);
    }

    protected abstract DetailsResult updateDetails(ISimpleCompany company, CompanyDetails details);

    public BukkitFutureResult<Boolean> submitCompanyPurge(SimpleCompany company) {
        return CompletableBukkitFuture.supplyAsync(() -> purgeCompany(company), executorService);
    }

    /**
     * Deletes a company together with its members, invites, applications and payout records.
     *
     * @param company company to delete
     * @return true if the company was deleted
     */
    protected abstract boolean purgeCompany(SimpleCompany company);

    public FutureResult<CompanyStats> retrieveCompanyStats(ISimpleCompany company) {
        return CompletableBukkitFuture.supplyAsync(() -> getCompanyStats(company));
    }

    protected abstract CompanyStats getCompanyStats(ISimpleCompany company);

    public FutureResult<Void> submitCompanyLevelUpdate(ISimpleCompany company, int level) {
        return CompletableBukkitFuture.runAsync(() -> updateCompanyLevel(company, level));
    }

    protected abstract void updateCompanyLevel(ISimpleCompany simpleCompany, int level);

    public FutureResult<Void> submitFailedOrder(ISimpleCompany company, int amount) {
        return CompletableBukkitFuture.runAsync(() -> upcountFailedOrders(company, amount));
    }

    public abstract void upcountFailedOrders(ISimpleCompany company, int amount);

    public FutureResult<Boolean> updateCompanyName(SimpleCompany company, String name) {
        return CompletableBukkitFuture.supplyAsync(() -> setCompanyName(company, name), executorService);
    }

    /**
     * Changes the name of a company.
     *
     * @param company company to rename
     * @param name    new name
     * @return true if the name was changed
     */
    protected abstract boolean setCompanyName(SimpleCompany company, String name);

    public BukkitFutureResult<Boolean> submitOwnerTransfer(ISimpleCompany company, UUID owner, UUID target) {
        return CompletableBukkitFuture.supplyAsync(() -> transferOwner(company, owner, target), executorService);
    }

    /**
     * Moves the ownership of a company from its owner to another member in one step.
     *
     * @param company company to transfer
     * @param owner   current owner
     * @param target  member who becomes the owner
     * @return true if the owner still owned the company, the target was a member and the ownership moved
     */
    protected abstract boolean transferOwner(ISimpleCompany company, UUID owner, UUID target);

    public abstract List<SimpleCompany> getCompanies();

    protected abstract List<CompanyMember> getCompanyMember(SimpleCompany company);

    protected abstract Optional<CompanyMember> getCompanyMember(OfflinePlayer player);

    protected abstract Optional<SimpleCompany> getSimpleCompany(int companyId);

    protected abstract SimpleCompany parseCompany(Row rs) throws SQLException;
}
