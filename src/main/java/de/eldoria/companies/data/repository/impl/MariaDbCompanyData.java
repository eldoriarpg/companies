package de.eldoria.companies.data.repository.impl;

import de.chojo.sqlutil.conversion.UUIDConverter;
import de.chojo.sqlutil.wrapper.QueryBuilderConfig;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

public class MariaDbCompanyData extends ACompanyData {

    public MariaDbCompanyData(DataSource dataSource, Plugin plugin, ExecutorService executorService) {
        super(QueryBuilderConfig.builder()
                .withExceptionHandler(e -> plugin.getLogger().log(Level.SEVERE, "Query exception", e))
                .build(), dataSource, executorService);
    }

    @Override
    protected void updateMember(CompanyMember member) {
        if (member.company() == -1) {
            builder()
                    .query("DELETE FROM company_member WHERE member_uuid = ?")
                    .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(member.uuid())))
                    .update().executeSync();
        } else {
            builder()
                    .query("REPLACE company_member(id, member_uuid, permission) VALUES(?,?,?)")
                    .paramsBuilder(stmt -> stmt.setInt(member.company()).setBytes(UUIDConverter.convert(member.uuid())).setLong(member.permission()))
                    .update().executeSync();
        }
    }

    // Company Profiles

    @Override
    protected Optional<CompanyProfile> toCompanyProfile(SimpleCompany simpleCompany) {
        return Optional.ofNullable(simpleCompany.toCompanyProfile(getCompanyMember(simpleCompany)));
    }

    @Override
    protected Optional<SimpleCompany> getPlayerCompany(OfflinePlayer player) {
        return builder(SimpleCompany.class)
                .query("SELECT c.id, c.name, c.founded FROM company_member LEFT JOIN companies c ON c.id = company_member.id WHERE member_uuid = ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())))
                .readRow(this::parseCompany)
                .firstSync();
    }

    @Override
    protected Optional<SimpleCompany> getCompanyByName(String name) {
        return builder(SimpleCompany.class)
                .query("SELECT c.id, c.name, c.founded FROM company_member LEFT JOIN companies c ON c.id = company_member.id WHERE c.name LIKE ?")
                .paramsBuilder(stmt -> stmt.setString(name))
                .readRow(this::parseCompany)
                .firstSync();
    }

    @Override
    protected Integer createCompany(String name) {
        return builder(Integer.class)
                .query("INSERT INTO companies(name) VALUES(?) RETURNING id")
                .paramsBuilder(stmt -> stmt.setString(name))
                .readRow(rs -> rs.getInt(1))
                .firstSync().get();
    }

    @Override
    protected SimpleCompany parseCompany(ResultSet rs) throws SQLException {
        return new SimpleCompany(rs.getInt("id"), rs.getString("name"),
                rs.getTimestamp("founded").toLocalDateTime());
    }

    @Override
    public void purgeCompany(SimpleCompany company) {
        var members = getCompanyMember(company);
        for (var member : members) {
            updateMember(member.kick());
        }
    }

    @Override
    protected List<CompanyMember> getCompanyMember(SimpleCompany company) {
        return builder(CompanyMember.class)
                .query("SELECT member_uuid, permission FROM company_member WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(company.id()))
                .readRow(rs -> CompanyMember.of(company.id(), UUIDConverter.convert(rs.getBytes("uuid")),
                        rs.getLong("permission")))
                .allSync();
    }

    @Override
    protected Optional<CompanyMember> getCompanyMember(OfflinePlayer player) {
        return builder(CompanyMember.class)
                .query("SELECT id, member_uuid, permission FROM company_member WHERE member_uuid = ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())))
                .readRow(rs -> CompanyMember.of(rs.getInt("id"), UUIDConverter.convert(rs.getBytes("uuid")),
                        rs.getLong("permission")))
                .firstSync();
    }

    @Override
    protected Optional<SimpleCompany> getSimpleCompany(int companyId) {
        return builder(SimpleCompany.class)
                .query("SELECT * FROM companies WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(companyId))
                .readRow(this::parseCompany)
                .firstSync();
    }
}
