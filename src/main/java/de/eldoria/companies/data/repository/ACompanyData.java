package de.eldoria.companies.data.repository;

import de.chojo.sqlutil.base.QueryFactoryHolder;
import de.chojo.sqlutil.wrapper.QueryBuilderConfig;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
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

public abstract class ACompanyData extends QueryFactoryHolder {
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

    public BukkitFutureResult<Optional<SimpleCompany>> retrievePlayerCompany(OfflinePlayer player) {
        return CompletableBukkitFuture.supplyAsync(() -> getPlayerCompany(player), executorService);
    }

    public BukkitFutureResult<Optional<CompanyProfile>> retrieveCompanyProfile(SimpleCompany simpleCompany) {
        return CompletableBukkitFuture.supplyAsync(() -> toCompanyProfile(simpleCompany));
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

    protected abstract SimpleCompany parseCompany(ResultSet rs) throws SQLException;

    public void submitCompanyPurge(SimpleCompany company) {
        CompletableFuture.runAsync(() -> purgeCompany(company));
    }

    protected abstract void purgeCompany(SimpleCompany company);

    public FutureResult<CompanyStats> retrieveCompanyStats(SimpleCompany company) {
        return CompletableBukkitFuture.supplyAsync(() -> getCompanyStats(company));
    }

    protected abstract CompanyStats getCompanyStats(SimpleCompany company);

    public FutureResult<Void> submitCompanyLevelUpdate(SimpleCompany company, int level) {
        return CompletableBukkitFuture.runAsync(() -> updateCompanyLevel(company, level));
    }

    protected abstract void updateCompanyLevel(SimpleCompany simpleCompany, int level);

    public FutureResult<Void> submitFailedOrder(SimpleCompany company, int amount) {
        return CompletableBukkitFuture.runAsync(() -> upcountFailedOrders(company, amount));
    }

    public abstract void upcountFailedOrders(SimpleCompany company, int amount);
}
