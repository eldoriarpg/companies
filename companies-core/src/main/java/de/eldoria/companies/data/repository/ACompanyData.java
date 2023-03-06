/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.base.QueryFactory;
import de.chojo.sadu.wrapper.QueryBuilderConfig;
import de.chojo.sadu.wrapper.util.Row;
import de.eldoria.companies.commands.company.TopOrder;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import de.eldoria.eldoutilities.threading.futures.FutureResult;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

public abstract class ACompanyData extends QueryFactory {
    private final ExecutorService executorService;

    public ACompanyData(Plugin plugin, DataSource dataSource, ExecutorService executorService) {
        super(dataSource, QueryBuilderConfig.builder()
                .withExceptionHandler(e -> plugin.getLogger().log(Level.SEVERE, "Query exception", e))
                .build());
        this.executorService = executorService;
    }

    public BukkitFutureResult<Void> submitMemberUpdate(CompanyMember member) {
        return CompletableBukkitFuture.runAsync(() -> updateMember(member));
    }

    protected abstract void updateMember(CompanyMember member);

    public BukkitFutureResult<Optional<? extends ISimpleCompany>> retrievePlayerCompany(OfflinePlayer player) {
        return CompletableBukkitFuture.supplyAsync(() -> getPlayerCompany(player), executorService);
    }

    public BukkitFutureResult<Optional<CompanyProfile>> retrieveCompanyProfile(ISimpleCompany simpleCompany) {
        return CompletableBukkitFuture.supplyAsync(() -> toCompanyProfile((SimpleCompany) simpleCompany));
    }

    protected abstract Optional<CompanyProfile> toCompanyProfile(SimpleCompany simpleCompany);

    public BukkitFutureResult<Optional<CompanyProfile>> retrievePlayerCompanyProfile(OfflinePlayer player) {
        return CompletableBukkitFuture.supplyAsync(() -> getPlayerCompany(player).map(company -> toCompanyProfile(company).get()));
    }

    protected abstract Optional<SimpleCompany> getPlayerCompany(OfflinePlayer player);

    public BukkitFutureResult<Optional<SimpleCompany>> retrieveCompanyByName(String name) {
        return CompletableBukkitFuture.supplyAsync(() -> getCompanyByName(name), executorService);
    }

    protected abstract Optional<SimpleCompany> getCompanyByName(String name);

    public BukkitFutureResult<Optional<SimpleCompany>> retrieveCompanyById(int id) {
        return CompletableBukkitFuture.supplyAsync(() -> getCompanyById(id), executorService);
    }

    protected abstract Optional<SimpleCompany> getCompanyById(int id);

    public BukkitFutureResult<Integer> submitCompanyCreation(String name) {
        return CompletableBukkitFuture.supplyAsync(() -> createCompany(name), executorService);
    }

    protected abstract Integer createCompany(String name);

    protected abstract List<CompanyMember> getCompanyMember(SimpleCompany company);

    protected abstract Optional<CompanyMember> getCompanyMember(OfflinePlayer player);

    protected abstract Optional<SimpleCompany> getSimpleCompany(int companyId);

    protected abstract SimpleCompany parseCompany(Row rs) throws SQLException;

    public void submitCompanyPurge(SimpleCompany company) {
        CompletableFuture.runAsync(() -> purgeCompany(company));
    }

    protected abstract void purgeCompany(SimpleCompany company);

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

    public FutureResult<List<CompanyRank>> retrieveRanking(TopOrder order, int page, int pageSize) {
        return CompletableBukkitFuture.supplyAsync(() -> getRanking(order, page, pageSize));
    }

    protected abstract List<CompanyRank> getRanking(TopOrder order, int page, int pageSize);

    public FutureResult<Void> updateCompanyName(SimpleCompany company, String name) {
        return CompletableBukkitFuture.runAsync(() -> setCompanyName(company, name));
    }

    protected abstract void setCompanyName(SimpleCompany company, String name);

    public abstract CompletableFuture<List<SimpleCompany>> getCompanies();
}
