package de.eldoria.companies.data;

import de.chojo.sqlutil.conversion.UUIDConverter;
import de.chojo.sqlutil.wrapper.QueryBuilderConfig;
import de.chojo.sqlutil.wrapper.QueryBuilderFactory;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class CompanyData extends QueryBuilderFactory {
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    public CompanyData(DataSource dataSource, Plugin plugin) {
        super(QueryBuilderConfig.builder()
                .withExceptionHandler(e -> plugin.getLogger().log(Level.SEVERE, "Query exception", e))
                .build(), dataSource);
    }


    private void updateMember(CompanyMember member) {
        builder()
                .query("REPLACE company_member(id, uuid, permission) VALUES(?,?,?)")
                .paramsBuilder(stmt -> stmt.setInt(member.company()).setBytes(UUIDConverter.convert(member.uuid())).setLong(member.permission()))
                .update().executeSync();
    }


    // Company Profiles

    public BukkitFutureResult<Optional<SimpleCompany>> retrievePlayerCompany(OfflinePlayer player) {
        return CompletableBukkitFuture.supplyAsync(() -> getPlayerCompany(player), executorService);
    }

    private Optional<SimpleCompany> getPlayerCompany(OfflinePlayer player) {
        return builder(SimpleCompany.class)
                .query("SELECT id, c.name, c.founded FROM company_member LEFT JOIN companies c ON c.id = company_member.id WHERE uuid = ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())))
                .readRow(rs -> new SimpleCompany(rs.getInt("id"), rs.getString("name"),
                        rs.getTimestamp("founded").toLocalDateTime()))
                .firstSync();
    }

    public BukkitFutureResult<Optional<CompanyProfile>> retrieveCompanyProfile(SimpleCompany simpleCompany) {
        return CompletableBukkitFuture.supplyAsync(() -> toCompanyProfile(simpleCompany));
    }

    public BukkitFutureResult<Optional<CompanyProfile>> retrievePlayerCompanyProfile(OfflinePlayer player) {
        return CompletableBukkitFuture.supplyAsync(() -> getPlayerCompany(player).map(company -> toCompanyProfile(company).get()));
    }

    public Optional<CompanyProfile> toCompanyProfile(SimpleCompany simpleCompany) {
        return Optional.ofNullable(simpleCompany.toCompanyProfile(getCompanyMember(simpleCompany.id())));
    }

    private List<CompanyMember> getCompanyMember(int companyId) {
        return builder(CompanyMember.class)
                .query("SELECT uuid, permission FROM company_member WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(companyId))
                .readRow(rs -> new CompanyMember(companyId, UUIDConverter.convert(rs.getBytes("uuid")),
                        rs.getLong("permission")))
                .allSync();
    }

    private Optional<CompanyMember> getCompanyMember(OfflinePlayer player) {
        return builder(CompanyMember.class)
                .query("SELECT id, uuid, permission FROM company_member WHERE uuid = ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())))
                .readRow(rs -> new CompanyMember(rs.getInt("id"), UUIDConverter.convert(rs.getBytes("uuid")),
                        rs.getLong("permission")))
                .firstSync();
    }

    private Optional<SimpleCompany> getSimpleCompany(int companyId) {
        return builder(SimpleCompany.class)
                .query("SELECT * FROM companies WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(companyId))
                .readRow(rs -> new SimpleCompany(rs.getInt("id"), rs.getString("name"),
                        rs.getTimestamp("founded").toLocalDateTime()))
                .firstSync();
    }

}
