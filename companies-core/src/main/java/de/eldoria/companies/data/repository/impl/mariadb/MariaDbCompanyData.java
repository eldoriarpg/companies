/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.wrapper.util.Row;
import de.eldoria.companies.commands.company.TopOrder;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.intellij.lang.annotations.Language;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import static de.eldoria.companies.data.StaticQueryAdapter.builder;

public class MariaDbCompanyData extends ACompanyData {

    public MariaDbCompanyData(ExecutorService executorService) {
        super(executorService);
    }

    @Override
    protected void updateMember(CompanyMember member) {
        if (member.company() == -1) {
            @Language("mariadb")
            var query = """
                DELETE
                FROM company_member
                WHERE member_uuid = ?""";
            builder()
                    .query(query)
                    .parameter(stmt -> stmt.setUuidAsBytes(member.uuid()))
                    .update()
                    .sendSync();
        } else {
            @Language("mariadb")
            var query = """
                    REPLACE company_member(id, member_uuid, permission)
                    VALUES (?, ?, ?)""";
            builder()
                    .query(query)
                    .parameter(stmt -> stmt.setInt(member.company())
                                           .setUuidAsBytes(member.uuid())
                                           .setLong(member.permission()))
                    .update()
                    .sendSync();
        }
    }

    // Company Profiles

    @Override
    protected Optional<CompanyProfile> toCompanyProfile(SimpleCompany simpleCompany) {
        return Optional.ofNullable(simpleCompany.toCompanyProfile(getCompanyMember(simpleCompany)));
    }

    @Override
    protected Optional<SimpleCompany> getPlayerCompany(OfflinePlayer player) {
        @Language("mariadb")
        var query = """
                SELECT c.id,
                       c.name,
                       c.founded,
                       c.level
                FROM company_member
                         LEFT JOIN companies c
                                   ON c.id = company_member.id
                WHERE member_uuid = ?""";
        return builder(SimpleCompany.class)
                .query(query)
                .parameter(stmt -> stmt.setUuidAsBytes(player.getUniqueId()))
                .readRow(this::parseCompany)
                .firstSync();
    }

    @Override
    protected Optional<SimpleCompany> getCompanyByName(String name) {
        @Language("mariadb")
        var query = """
                SELECT c.id,
                       c.name,
                       c.founded,
                       c.level
                FROM company_member
                         LEFT JOIN companies c
                                   ON c.id = company_member.id
                WHERE c.name LIKE ?""";
        return builder(SimpleCompany.class)
                .query(query)
                .parameter(stmt -> stmt.setString(name))
                .readRow(this::parseCompany)
                .firstSync();
    }

    @Override
    protected Optional<SimpleCompany> getCompanyById(int id) {
        @Language("mariadb")
        var query = """
                SELECT id, name, founded, level
                FROM companies
                WHERE id = ?""";
        return builder(SimpleCompany.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(id))
                .readRow(this::parseCompany)
                .firstSync();
    }

    @Override
    protected Integer createCompany(String name) {
        @Language("mariadb")
        var query = """
                INSERT INTO companies(name)
                VALUES (?)
                RETURNING id""";
        return builder(Integer.class)
                .query(query)
                .parameter(stmt -> stmt.setString(name))
                .readRow(rs -> rs.getInt(1))
                .firstSync()
                .get();
    }

    @Override
    protected SimpleCompany parseCompany(Row rs) throws SQLException {
        return new SimpleCompany(rs.getInt("id"), rs.getString("name"),
                rs.getTimestamp("founded")
                  .toLocalDateTime(), rs.getInt("level"));
    }

    @Override
    public void purgeCompany(SimpleCompany company) {
        var members = getCompanyMember(company);
        for (var member : members) {
            updateMember(member.kick());
        }
    }

    @Override
    protected CompanyStats getCompanyStats(ISimpleCompany company) {
        @Language("mariadb")
        var query = """
                SELECT id,
                       name,
                       founded,
                       member_count,
                       order_count,
                       price,
                       amount
                FROM company_stats_view
                WHERE id = ?""";
        return builder(CompanyStats.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(company.id()))
                .readRow(this::parseCompanyStats)
                .firstSync()
                .get();
    }

    @Override
    protected void updateCompanyLevel(ISimpleCompany company, int level) {
        @Language("mariadb")
        var query = """
                UPDATE companies
                SET level = ?
                WHERE id = ?""";
        builder().query(query)
                 .parameter(stmt -> stmt.setInt(level)
                                        .setInt(company.id()))
                 .update()
                 .sendSync();
    }

    @Override
    public void upcountFailedOrders(ISimpleCompany company, int amount) {
        @Language("mariadb")
        var query = """
                INSERT
                INTO company_stats(id, failed_orders)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE failed_orders = failed_orders + VALUES(failed_orders)""";
        builder()
                .query(query)
                .parameter(stmt -> stmt.setInt(company.id())
                                       .setInt(amount))
                .update()
                .sendSync();
    }

    @Override
    protected List<CompanyRank> getRanking(TopOrder order, int page, int pageSize) {
        @Language("mariadb")
        var query = """
                SELECT ROW_NUMBER() OVER (ORDER BY %s) AS comp_rank,
                       id,
                       name,
                       founded,
                       member_count,
                       order_count,
                       price,
                       amount
                FROM company_stats_view
                ORDER BY comp_rank
                LIMIT ? OFFSET ?""";
        return builder(CompanyRank.class)
                .query(query, order.orderColumn())
                .parameter(stmt -> stmt.setInt(pageSize)
                                       .setInt((page - 1) * pageSize))
                .readRow(rs -> parseCompanyStats(rs).toRank(rs.getInt("comp_rank")))
                .allSync();
    }

    @Override
    protected void setCompanyName(SimpleCompany company, String name) {
        @Language("mariadb")
        var query = """
                UPDATE companies
                SET name = ?
                WHERE id = ?""";
        builder()
                .query(query)
                .parameter(stmt -> stmt.setString(name)
                                       .setInt(company.id()))
                .update()
                .sendSync();
    }

    @Override
    public CompletableFuture<List<SimpleCompany>> getCompanies() {
        @Language("mariadb")
        var query = """
                SELECT id, name, founded, level
                FROM companies""";
        return builder(SimpleCompany.class)
                .queryWithoutParams(query)
                .readRow(this::parseCompany)
                .all();
    }

    protected CompanyStats parseCompanyStats(Row rs) throws SQLException {
        return new CompanyStats(rs.getInt("id"), rs.getString("name"), rs.getTimestamp("founded")
                                                                         .toLocalDateTime(),
                rs.getInt("member_count"), rs.getInt("order_count"), rs.getDouble("price"), rs.getInt("amount"));
    }

    @Override
    protected List<CompanyMember> getCompanyMember(SimpleCompany company) {
        @Language("mariadb")
        var query = """
                SELECT member_uuid, permission
                FROM company_member
                WHERE id = ?""";
        return builder(CompanyMember.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(company.id()))
                .readRow(rs -> CompanyMember.of(company.id(), rs.getUuidFromBytes("member_uuid"),
                        rs.getLong("permission")))
                .allSync();
    }

    @Override
    protected Optional<CompanyMember> getCompanyMember(OfflinePlayer player) {
        @Language("mariadb")
        var query = """
                SELECT id, member_uuid, permission
                FROM company_member
                WHERE member_uuid = ?""";
        return builder(CompanyMember.class)
                .query(query)
                .parameter(stmt -> stmt.setUuidAsBytes(player.getUniqueId()))
                .readRow(rs -> CompanyMember.of(rs.getInt("id"), rs.getUuidFromBytes("member_uuid"),
                        rs.getLong("permission")))
                .firstSync();
    }

    @Override
    protected Optional<SimpleCompany> getSimpleCompany(int companyId) {
        @Language("mariadb")
        var query = """
                SELECT id, name, founded, level
                FROM companies
                WHERE id = ?""";
        return builder(SimpleCompany.class)
                .query(query)
                .parameter(stmt -> stmt.setInt(companyId))
                .readRow(this::parseCompany)
                .firstSync();
    }
}
