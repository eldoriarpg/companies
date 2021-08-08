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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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

    public BukkitFutureResult<Void> submitMemberUpdate(CompanyMember member) {
        return CompletableBukkitFuture.runAsync(() -> updateMember(member));
    }

    private void updateMember(CompanyMember member) {
        if (member.company() == -1) {
            builder()
                    .query("DELETE FROM company_member WHERE uuid = ?")
                    .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(member.uuid())))
                    .update().executeSync();
        } else {
            builder()
                    .query("REPLACE company_member(id, uuid, permission) VALUES(?,?,?)")
                    .paramsBuilder(stmt -> stmt.setInt(member.company()).setBytes(UUIDConverter.convert(member.uuid())).setLong(member.permission()))
                    .update().executeSync();
        }
    }


    // Company Profiles

    public BukkitFutureResult<Optional<SimpleCompany>> retrievePlayerCompany(OfflinePlayer player) {
        return CompletableBukkitFuture.supplyAsync(() -> getPlayerCompany(player), executorService);
    }

    public BukkitFutureResult<Optional<CompanyProfile>> retrieveCompanyProfile(SimpleCompany simpleCompany) {
        return CompletableBukkitFuture.supplyAsync(() -> toCompanyProfile(simpleCompany));
    }

    public Optional<CompanyProfile> toCompanyProfile(SimpleCompany simpleCompany) {
        return Optional.ofNullable(simpleCompany.toCompanyProfile(getCompanyMember(simpleCompany)));
    }

    public BukkitFutureResult<Optional<CompanyProfile>> retrievePlayerCompanyProfile(OfflinePlayer player) {
        return CompletableBukkitFuture.supplyAsync(() -> getPlayerCompany(player).map(company -> toCompanyProfile(company).get()));
    }

    private Optional<SimpleCompany> getPlayerCompany(OfflinePlayer player) {
        return builder(SimpleCompany.class)
                .query("SELECT c.id, c.name, c.founded FROM company_member LEFT JOIN companies c ON c.id = company_member.id WHERE uuid = ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())))
                .readRow(this::parseCompany)
                .firstSync();
    }

    public BukkitFutureResult<Optional<SimpleCompany>> retrieveCompanyByName(String name) {
        return CompletableBukkitFuture.supplyAsync(() -> getCompanyByName(name), executorService);
    }

    private Optional<SimpleCompany> getCompanyByName(String name) {
        return builder(SimpleCompany.class)
                .query("SELECT c.id, c.name, c.founded FROM company_member LEFT JOIN companies c ON c.id = company_member.id WHERE c.name LIKE ?")
                .paramsBuilder(stmt -> stmt.setString(name))
                .readRow(this::parseCompany)
                .firstSync();
    }

    public BukkitFutureResult<Integer> submitCompanyCreation(String name) {
        return CompletableBukkitFuture.supplyAsync(() -> createCompany(name), executorService);
    }

    private Integer createCompany(String name) {
        return builder(Integer.class)
                .query("INSERT INTO companies(name) VALUES(?) RETURNING id")
                .paramsBuilder(stmt -> stmt.setString(name))
                .readRow(rs -> rs.getInt(1))
                .firstSync().get();
    }

    private List<CompanyMember> getCompanyMember(SimpleCompany company) {
        return builder(CompanyMember.class)
                .query("SELECT uuid, permission FROM company_member WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(company.id()))
                .readRow(rs -> CompanyMember.of(company.id(), UUIDConverter.convert(rs.getBytes("uuid")),
                        rs.getLong("permission")))
                .allSync();
    }

    private Optional<CompanyMember> getCompanyMember(OfflinePlayer player) {
        return builder(CompanyMember.class)
                .query("SELECT id, uuid, permission FROM company_member WHERE uuid = ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())))
                .readRow(rs -> CompanyMember.of(rs.getInt("id"), UUIDConverter.convert(rs.getBytes("uuid")),
                        rs.getLong("permission")))
                .firstSync();
    }

    private Optional<SimpleCompany> getSimpleCompany(int companyId) {
        return builder(SimpleCompany.class)
                .query("SELECT * FROM companies WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(companyId))
                .readRow(this::parseCompany)
                .firstSync();
    }

    private SimpleCompany parseCompany(ResultSet rs) throws SQLException {
        return new SimpleCompany(rs.getInt("id"), rs.getString("name"),
                rs.getTimestamp("founded").toLocalDateTime());
    }

    public void submitCompanyPurge(SimpleCompany company) {
        CompletableFuture.runAsync(() -> purgeCompany(company));
    }

    private void purgeCompany(SimpleCompany company) {
        var members = getCompanyMember(company);
        for (var member : members) {
            updateMember(member.kick());
        }
    }
}
