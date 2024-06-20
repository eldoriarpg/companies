/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.eldoria.companies.commands.company.TopOrder;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import org.bukkit.OfflinePlayer;
import org.intellij.lang.annotations.Language;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

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
            query(query)
                    .single(call().bind(member.uuid(), UUID_BYTES))
                    .update();
        } else {
            @Language("mariadb")
            var query = """
                    REPLACE company_member(id, member_uuid, permission)
                    VALUES (?, ?, ?)""";
            query(query)
                    .single(call().bind(member.company())
                            .bind(member.uuid(), UUID_BYTES)
                            .bind(member.permission()))
                    .update();
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
        return query(query)
                .single(call().bind(player.getUniqueId(), UUID_BYTES))
                .map(this::parseCompany)
                .first();
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
        return query(query)
                .single(call().bind(name))
                .map(this::parseCompany)
                .first();
    }

    @Override
    protected Optional<SimpleCompany> getCompanyById(int id) {
        @Language("mariadb")
        var query = """
                SELECT id, name, founded, level
                FROM companies
                WHERE id = ?""";
        return query(query)
                .single(call().bind(id))
                .map(this::parseCompany)
                .first();
    }

    @Override
    protected Integer createCompany(String name) {
        @Language("mariadb")
        var query = """
                INSERT INTO companies(name)
                VALUES (?)
                RETURNING id""";
        return query(query)
                .single(call().bind(name))
                .map(rs -> rs.getInt(1))
                .first()
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
        return query(query)
                .single(call().bind(company.id()))
                .map(this::parseCompanyStats)
                .first()
                .get();
    }

    @Override
    protected void updateCompanyLevel(ISimpleCompany company, int level) {
        @Language("mariadb")
        var query = """
                UPDATE companies
                SET level = ?
                WHERE id = ?""";
        query(query)
                .single(call().bind(level)
                        .bind(company.id()))
                .update();
    }

    @Override
    public void upcountFailedOrders(ISimpleCompany company, int amount) {
        @Language("mariadb")
        var query = """
                INSERT
                INTO company_stats(id, failed_orders)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE failed_orders = failed_orders + VALUES(failed_orders)""";
        query(query)
                .single(call().bind(company.id())
                        .bind(amount))
                .update();
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
        return query(query, order.orderColumn())
                .single(call().bind(pageSize)
                        .bind((page - 1) * pageSize))
                .map(rs -> parseCompanyStats(rs).toRank(rs.getInt("comp_rank")))
                .all();
    }

    @Override
    protected void setCompanyName(SimpleCompany company, String name) {
        @Language("mariadb")
        var query = """
                UPDATE companies
                SET name = ?
                WHERE id = ?""";
        query(query)
                .single(call().bind(name)
                        .bind(company.id()))
                .update();
    }

    @Override
    public List<SimpleCompany> getCompanies() {
        @Language("mariadb")
        var query = """
                SELECT id, name, founded, level
                FROM companies""";
        return query(query)
                .single()
                .map(this::parseCompany)
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
        return query(query)
                .single(call().bind(company.id()))
                .map(rs -> CompanyMember.of(company.id(), rs.get("member_uuid", UUID_BYTES),
                        rs.getLong("permission")))
                .all();
    }

    @Override
    protected Optional<CompanyMember> getCompanyMember(OfflinePlayer player) {
        @Language("mariadb")
        var query = """
                SELECT id, member_uuid, permission
                FROM company_member
                WHERE member_uuid = ?""";
        return query(query)
                .single(call().bind(player.getUniqueId(), UUID_BYTES))
                .map(rs -> CompanyMember.of(rs.getInt("id"), rs.get("member_uuid", UUID_BYTES),
                        rs.getLong("permission")))
                .first();
    }

    @Override
    protected Optional<SimpleCompany> getSimpleCompany(int companyId) {
        @Language("mariadb")
        var query = """
                SELECT id, name, founded, level
                FROM companies
                WHERE id = ?""";
        return query(query)
                .single(call().bind(companyId))
                .map(this::parseCompany)
                .first();
    }
}
